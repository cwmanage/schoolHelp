package com.schoolhelp.user.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolhelp.common.exception.BusinessException;
import com.schoolhelp.user.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 主循环（手写 function-calling，无框架）：
 * messages = [system, user] -> 调 LLM(带 tools) ->
 *   若返回 tool_calls：本地执行工具 -> 结果以 role=tool 回填 -> 再调 LLM（最多 MAX_ROUNDS 轮）
 *   若返回 content：作为最终答案
 *
 * 并发保护：ModelPool 按模型占用（智谱免费档每模型 1 并发，占用式）；上游 429 自动重试一次。
 * 限流：每用户滑动窗口（独立于普通问答计数）。
 */
@Slf4j
@Service
public class AgentChatService {

    private static final int MAX_ROUNDS = 5;
    private static final String SYSTEM_PROMPT = "你是校园学习助手「课屿」的智能体。今天是 %s（%s）。"
            + "回答要求：1) 简体中文，简洁准确；2) 涉及用户的课程、天气等个人数据时必须调用工具获取，禁止编造；"
            + "3) 需要多条信息时可以连续调用多个工具；4) 不讨论政治敏感、违法违规内容。";

    private final AiProperties props;
    private final AgentToolRegistry toolRegistry;
    private final ModelPool modelPool;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    /** 限流窗口：userId -> (窗口起始秒, 已用次数)——与普通问答分开计数 */
    private final ConcurrentHashMap<Long, long[]> rateWindows = new ConcurrentHashMap<>();

    public AgentChatService(AiProperties props, AgentToolRegistry toolRegistry,
                            ModelPool modelPool, ObjectMapper objectMapper) {
        this.props = props;
        this.toolRegistry = toolRegistry;
        this.modelPool = modelPool;
        this.objectMapper = objectMapper;
        // 独立 RestClient：agent 多轮循环耗时较长，读超时放宽到 120s
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(10));
        f.setReadTimeout(Duration.ofSeconds(120));
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(f)
                .build();
    }

    /** 返回 {answer, rounds, toolTrace:[{tool,args}]}，toolTrace 供前端展示 Agent 的执行过程 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(Long userId, String userMessage) {
        if (!props.isConfigured()) {
            throw new BusinessException("AI 功能未配置（缺少 API Key），请联系管理员");
        }
        checkRateLimit(userId);

        String agentModel = props.getAgentModel();
        LocalDate today = LocalDate.now();
        String system = String.format(SYSTEM_PROMPT,
                today, today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA));

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        messages.add(Map.of("role", "user", "content", userMessage));

        List<Map<String, Object>> toolTrace = new ArrayList<>();
        String model = agentModel;

        try {
            for (int round = 0; round < MAX_ROUNDS; round++) {
                // 占用模型并发名额（其他用户请求在此排队；智谱免费档每模型 1 并发）
                if (!modelPool.tryAcquire(model, 25)) {
                    throw new BusinessException("当前使用人数较多，请稍后再试");
                }
                Map<String, Object> resp;
                try {
                    resp = callLlm(model, messages);
                } finally {
                    modelPool.release(model);   // 响应结束才释放——匹配「占用式」限流
                }

                List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
                if (choices == null || choices.isEmpty()) {
                    throw new BusinessException("AI 未返回结果，请稍后再试");
                }
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                List<Map<String, Object>> toolCalls =
                        message == null ? null : (List<Map<String, Object>>) message.get("tool_calls");

                if (toolCalls == null || toolCalls.isEmpty()) {
                    // 无工具调用 = 最终答案
                    String answer = message == null ? null : String.valueOf(message.get("content"));
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("answer", answer == null || answer.isBlank() ? "（空回复）" : answer);
                    out.put("rounds", round + 1);
                    out.put("toolTrace", toolTrace);
                    return out;
                }

                // 原样回填 assistant 消息（含 tool_calls），再逐个执行工具并回填结果——两者必须成对
                messages.add(message);
                for (Map<String, Object> tc : toolCalls) {
                    Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                    String name = String.valueOf(fn.get("name"));
                    String args = fn.get("arguments") == null ? "{}" : String.valueOf(fn.get("arguments"));
                    log.info("Agent[用户{}] round{} 调用工具 {} args={}", userId, round + 1, name, args);
                    toolTrace.add(Map.of("tool", name, "args", args));
                    String result = toolRegistry.execute(name, args);
                    messages.add(Map.of("role", "tool", "tool_call_id", tc.get("id"), "content", result));
                }
            }
            return Map.of("answer", "这个问题需要太多步骤了，请拆开来问～", "rounds", MAX_ROUNDS,
                    "toolTrace", toolTrace);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("请求被中断，请稍后再试");
        }
    }

    /** 调用 LLM（OpenAI 兼容），上游 429 自动重试一次（模型池已把本服务并发钳到 1，429 多来自同 Key 其他功能） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callLlm(String model, List<Map<String, Object>> messages) {
        for (int attempt = 0; attempt < 2; attempt++) {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("tools", toolRegistry.openaiTools());
            body.put("tool_choice", "auto");
            body.put("stream", false);
            body.put("max_tokens", 2048);
            body.put("temperature", 0.3);
            // 工具调用场景关闭深度思考：更快，且 4.7 系非思考模式下的 tool_calls 更稳定
            body.put("thinking", Map.of("type", "disabled"));

            try {
                Map<String, Object> resp = restClient.post()
                        .uri("/chat/completions")
                        .header("Authorization", "Bearer " + props.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(Map.class);
                if (resp != null) return resp;
            } catch (RestClientException e) {
                boolean tooMany = String.valueOf(e.getMessage()).contains("429");
                log.warn("LLM 调用异常(第{}次): {}", attempt + 1, e.getMessage());
                if (!tooMany || attempt == 1) {
                    throw new BusinessException("AI 服务暂时不可用，请稍后再试");
                }
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException("请求被中断，请稍后再试");
                }
            }
        }
        throw new BusinessException("AI 服务暂时不可用，请稍后再试");
    }

    private void checkRateLimit(Long userId) {
        long nowSec = System.currentTimeMillis() / 1000;
        long[] window = rateWindows.compute(userId, (k, v) ->
                v == null || nowSec - v[0] >= 60 ? new long[]{nowSec, 0} : v);
        if (window[1] >= props.getRateLimitPerMinute()) {
            throw new BusinessException("提问太频繁啦，请稍后再试（每分钟 " + props.getRateLimitPerMinute() + " 次）");
        }
        window[1]++;
    }
}
