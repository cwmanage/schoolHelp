package com.schoolhelp.user.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolhelp.common.exception.BusinessException;
import com.schoolhelp.user.config.AiProperties;
import com.schoolhelp.user.entity.AgentChatMessage;
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
 * messages = [system, ...历史(按 userId 从 MySQL 记忆读取, 最近8条), user] -> 按降级链调 LLM(带 tools) ->
 *   若返回 tool_calls：本地执行工具 -> 结果以 role=tool 回填 -> 再调 LLM（最多 MAX_ROUNDS 轮）
 *   若返回 content：作为最终答案
 *
 * 模型降级链（多 Key 端点，ModelPool.chain）：
 *   main Key：glm-4.7-flash → glm-4.5-flash → glm-4-flash（全免费，每模型独立 1 并发）
 *   trial Key：glm-5.3-flash → glm-5.3-flashx（资源包，2027-02-03 到期，并发更高）
 * 限流应对：某端点 429/1305/1302/1113 → 熔断 60s + 立即切换下一个端点（不同 Key/模型限速独立）。
 * 记忆：ChatMemoryService 按 userId 持久化到 MySQL（跨设备），每轮结束追加 user+assistant。
 */
@Slf4j
@Service
public class AgentChatService {

    private static final int MAX_ROUNDS = 5;
    private static final String SYSTEM_PROMPT = "你是校园学习助手「课屿」的智能体。今天是 %s（%s）。"
            + "回答要求：1) 简体中文，简洁准确；2) 涉及用户的课程、天气等个人数据时必须调用工具获取，禁止编造；"
            + "3) 需要多条信息时可以连续调用多个工具；4) 对话历史中已包含的数据（如上轮刚查过的课表/天气）可直接引用回答追问，无需重复调用；"
            + "5) 不讨论政治敏感、违法违规内容。";

    private final AiProperties props;
    private final AgentToolRegistry toolRegistry;
    private final ModelPool modelPool;
    private final ChatMemoryService memory;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    /** 限流窗口：userId -> (窗口起始秒, 已用次数)——与普通问答分开计数 */
    private final ConcurrentHashMap<Long, long[]> rateWindows = new ConcurrentHashMap<>();

    public AgentChatService(AiProperties props, AgentToolRegistry toolRegistry,
                            ModelPool modelPool, ChatMemoryService memory, ObjectMapper objectMapper) {
        this.props = props;
        this.toolRegistry = toolRegistry;
        this.modelPool = modelPool;
        this.memory = memory;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(10));
        f.setReadTimeout(Duration.ofSeconds(120));
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(f)
                .build();
    }

    /** 返回 {answer, rounds, usedModel, toolTrace}，toolTrace 供前端展示 Agent 的执行过程 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(Long userId, String userMessage) {
        if (!props.isConfigured()) {
            throw new BusinessException("AI 功能未配置（缺少 API Key），请联系管理员");
        }
        checkRateLimit(userId);

        LocalDate today = LocalDate.now();
        String system = String.format(SYSTEM_PROMPT,
                today, today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA));

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        // 多轮记忆：从 MySQL 读取最近 8 条（跨设备持久化）
        for (AgentChatMessage m : memory.loadRecent(userId)) {
            messages.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        List<Map<String, Object>> toolTrace = new ArrayList<>();
        String answer = null;
        String usedModel = null;
        int rounds = 0;

        // 降级链（多 Key 端点）：免费模型池 → 试用 Key 资源包模型（429/1305/1302/1113 时自动切换）
        for (ModelPool.LlmEndpoint ep : modelPool.chain()) {
            try {
                for (int round = 0; round < MAX_ROUNDS; round++) {
                    boolean acquired;
                    try {
                        acquired = modelPool.tryAcquire(ep, 10);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ModelBusyException();   // 中断按忙处理，触发降级链
                    }
                    if (!acquired) {
                        throw new ModelBusyException();   // 该端点并发被占（本服务内），切下一个
                    }
                    Map<String, Object> resp;
                    try {
                        resp = callLlm(ep, messages);
                    } finally {
                        modelPool.release(ep);   // 响应结束才释放——匹配「占用式」限流
                    }

                    List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
                    if (choices == null || choices.isEmpty()) {
                        throw new BusinessException("AI 未返回结果，请稍后再试");
                    }
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    List<Map<String, Object>> toolCalls =
                            message == null ? null : (List<Map<String, Object>>) message.get("tool_calls");

                    if (toolCalls == null || toolCalls.isEmpty()) {
                        String content = message == null ? null : String.valueOf(message.get("content"));
                        answer = content == null || content.isBlank() ? "（空回复）" : content;
                        usedModel = ep.model();
                        rounds = round + 1;
                        break;
                    }

                    messages.add(message);
                    for (Map<String, Object> tc : toolCalls) {
                        Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                        String name = String.valueOf(fn.get("name"));
                        String args = fn.get("arguments") == null ? "{}" : String.valueOf(fn.get("arguments"));
                        log.info("Agent[用户{}] {} round{} 工具 {} args={}", userId, ep.model(), round + 1, name, args);
                        toolTrace.add(Map.of("tool", name, "args", args));
                        String result = toolRegistry.execute(name, args);
                        messages.add(Map.of("role", "tool", "tool_call_id", tc.get("id"), "content", result));
                    }
                }
                if (answer != null) break;   // 当前端点跑出最终答案，不再降级
                log.warn("端点 {} 达到循环上限未出答案，尝试降级链下一个", ep.id());
            } catch (ModelBusyException e) {
                modelPool.markThrottled(ep);   // 熔断：60s 内跳过，高峰自动整体降级到下一个 Key/模型
                log.info("端点 {} 暂时不可用（限流/占用），尝试降级链下一个", ep.id());
            }
        }

        if (answer == null) {
            throw new BusinessException("所有模型通道均繁忙，请几分钟后再试");
        }

        // 记忆持久化：本轮问答入库（跨设备、大赛多轮记忆考点）
        memory.append(userId, userMessage, answer);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("answer", answer);
        out.put("rounds", rounds);
        out.put("usedModel", usedModel);
        out.put("toolTrace", toolTrace);
        return out;
    }

    /**
     * 调用 LLM（单次，无内部重试）：任何网络/限流异常都抛 ModelBusyException 触发降级链。
     * 1305/1302 限流在高峰期响应极慢且变体多（429/1305/1302/非JSON错误体），
     * 内部重试只会加剧 Key 级 RPM 限流——正确解法是「立即切换下一个端点」（不同 Key/模型的限速独立）。
     * 例外：认证类错误（401/403）降级无意义，直接报错。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callLlm(ModelPool.LlmEndpoint ep, List<Map<String, Object>> messages) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", ep.model());
        body.put("messages", messages);
        body.put("tools", toolRegistry.openaiTools());
        body.put("tool_choice", "auto");
        body.put("stream", false);
        body.put("max_tokens", 2048);
        body.put("temperature", 0.3);
        body.put("thinking", Map.of("type", "disabled"));   // 工具场景关深度思考

        try {
            Map<String, Object> resp = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + ep.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (resp != null) return resp;
            throw new ModelBusyException();
        } catch (RestClientException e) {
            String msg = String.valueOf(e.getMessage());
            log.warn("LLM[{}] 调用失败: {}", ep.id(), msg);
            // 认证类错误：换模型/Key 也无意义（同一配置错误），直接报错
            if (msg.contains("401") || msg.contains("403")) {
                throw new BusinessException("AI 服务认证失败，请联系管理员检查 API Key");
            }
            throw new ModelBusyException();   // 其余（429/1305/1302/1113/解析失败/超时等）全部降级
        }
    }

    /** 清空当前用户的对话记忆 */
    public void clear(Long userId) {
        memory.clear(userId);
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

    /** 模型忙（限流型失败）——触发降级链切换下一个端点 */
    private static class ModelBusyException extends RuntimeException {}
}
