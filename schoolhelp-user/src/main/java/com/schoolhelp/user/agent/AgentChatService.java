package com.schoolhelp.user.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolhelp.common.exception.BusinessException;
import com.schoolhelp.user.config.AiProperties;
import com.schoolhelp.user.entity.AgentChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 编排器（五层架构的编排层）：
 * 阶段1（决策循环）：LITE 档端点链跑 function-calling 循环（工具调用 + 数据获取）
 * 阶段2（可选精炼）：polishWithFlagship=true 且用过工具时，FLAGSHIP 档基于工具数据精炼最终回答
 * 降级链：链内端点逐个尝试（ModelBusy → 熔断标记 → 下一个）；会话级 deadline（150s）防无限耗时
 * 记忆：ChatMemoryService 按 userId+sessionId 持久化到 MySQL
 */
@Slf4j
@Service
public class AgentChatService {

    private static final int MAX_ROUNDS = 5;
    private static final long DEADLINE_MS = 150_000;   // 会话时间预算：150s（nginx 180s 内留余量）
    private static final String SESSION_ID = "default";

    private final AiProperties props;
    private final AgentToolRegistry toolRegistry;
    private final ModelPool modelPool;
    private final ChatMemoryService memory;
    private final LlmClient llmClient;
    private final ContextManager contextManager;
    private final ObjectMapper objectMapper;

    /** 限流窗口：userId -> (窗口起始秒, 已用次数)——与普通问答分开计数 */
    private final ConcurrentHashMap<Long, long[]> rateWindows = new ConcurrentHashMap<>();

    public AgentChatService(AiProperties props, AgentToolRegistry toolRegistry,
                            ModelPool modelPool, ChatMemoryService memory,
                            LlmClient llmClient, ContextManager contextManager,
                            ObjectMapper objectMapper) {
        this.props = props;
        this.toolRegistry = toolRegistry;
        this.modelPool = modelPool;
        this.memory = memory;
        this.llmClient = llmClient;
        this.contextManager = contextManager;
        this.objectMapper = objectMapper;
    }

    /** 返回 {answer, rounds, usedModel, toolTrace, tokensUsed} */
    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(Long userId, String userMessage) {
        if (!props.isConfigured()) {
            throw new BusinessException("AI 功能未配置（缺少 API Key），请联系管理员");
        }
        checkRateLimit(userId);

        // 显式北京时间（防 JVM/环境时区漂移导致凌晨日期错位）
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        String sysPrompt = props.getAgentSystemPrompt() == null || props.getAgentSystemPrompt().isBlank()
                ? "你是校园学习助手「课屿」的智能体。今天是 %s（%s）。"
                + "回答要求：1) 简体中文，简洁准确；2) 涉及用户的课程、天气等个人数据时必须调用工具获取，禁止编造；"
                + "3) 需要多条信息时可以连续调用多个工具；4) 对话历史中已包含的数据可直接引用回答追问，无需重复调用；"
                + "5) 不讨论政治敏感、违法违规内容。"
                : props.getAgentSystemPrompt();
        String system = String.format(sysPrompt,
                today, today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA));

        long deadline = System.currentTimeMillis() + DEADLINE_MS;
        List<Map<String, Object>> toolTrace = new ArrayList<>();

        // ===== 阶段 1：决策循环（LITE 链，工具调用 + 数据获取）=====
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        for (AgentChatMessage m : memory.loadRecent(userId, SESSION_ID)) {
            messages.add(contextManager.toMessage(m));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        String draft = null;
        String usedModel = null;
        int rounds = 0;
        long tokens = 0;

        for (ModelPool.LlmEndpoint ep : modelPool.agentChain()) {
            try {
                for (int round = 0; round < MAX_ROUNDS; round++) {
                    if (System.currentTimeMillis() > deadline) {
                        log.warn("会话超时（deadline），停止循环");
                        throw new ModelBusyException();
                    }
                    boolean acquired;
                    try {
                        acquired = modelPool.tryAcquire(ep, 10);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ModelBusyException();
                    }
                    if (!acquired) throw new ModelBusyException();
                    Map<String, Object> resp;
                    try {
                        // 工具决策轮：关闭深度思考（快速结构化输出）
                        resp = llmClient.chat(ep, contextManager.truncateForProfile(messages, ep.profile()),
                                false, toolRegistry.openaiTools());
                        tokens += llmClient.extractTokens(resp);
                    } finally {
                        modelPool.release(ep);
                    }

                    List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
                    if (choices == null || choices.isEmpty()) throw new ModelBusyException();
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    List<Map<String, Object>> toolCalls =
                            message == null ? null : (List<Map<String, Object>>) message.get("tool_calls");

                    if (toolCalls == null || toolCalls.isEmpty()) {
                        String content = message == null ? null : String.valueOf(message.get("content"));
                        draft = content == null || content.isBlank() ? "（空回复）" : content;
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
                if (draft != null) break;
                log.warn("端点 {} 达到循环上限未出答案，尝试降级链下一个", ep.id());
            } catch (ModelBusyException e) {
                modelPool.markThrottled(ep);
                log.info("端点 {} 暂时不可用（限流/占用），尝试降级链下一个", ep.id());
            }
        }
        if (draft == null) {
            throw new BusinessException("所有模型通道均繁忙，请几分钟后再试");
        }

        // ===== 阶段 2（可选）：FLAGSHIP 精炼最终回答 =====
        boolean polished = false;
        if (props.getOrchestrator().isPolishWithFlagship() && !toolTrace.isEmpty()) {
            List<Map<String, Object>> polishMessages = new ArrayList<>();
            polishMessages.add(Map.of("role", "system", "content",
                    system + "\n\n你已通过工具获取了所需数据，请基于对话与工具结果，给出最终、准确、简洁的回答。"));
            for (AgentChatMessage m : memory.loadRecent(userId, SESSION_ID)) {
                polishMessages.add(contextManager.toMessage(m));
            }
            polishMessages.add(Map.of("role", "user", "content", userMessage));
            polishMessages.add(Map.of("role", "assistant", "content", draft));
            polishMessages.add(Map.of("role", "user",
                    "content", "请基于以上工具获取的数据，重新组织并给出最终回答（保留关键数据，使用简体中文）。"));

            for (ModelPool.LlmEndpoint ep : modelPool.chainForTier(
                    props.getOrchestrator().getFinalAnswerTier())) {
                try {
                    if (System.currentTimeMillis() > deadline) break;
                    boolean acquired2;
                    try {
                        acquired2 = modelPool.tryAcquire(ep, 10);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ModelBusyException();
                    }
                    if (!acquired2) throw new ModelBusyException();
                    Map<String, Object> resp;
                    try {
                        // 最终回答轮：开启深度思考（FLAGSHIP 质量）
                        resp = llmClient.chat(ep, contextManager.truncateForProfile(polishMessages, ep.profile()),
                                true, null);
                        tokens += llmClient.extractTokens(resp);
                    } finally {
                        modelPool.release(ep);
                    }
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                        String content = message == null ? null : String.valueOf(message.get("content"));
                        if (content != null && !content.isBlank()) {
                            draft = content;
                            polished = true;
                            break;
                        }
                    }
                } catch (ModelBusyException e) {
                    modelPool.markThrottled(ep);
                }
            }
        }

        // ===== 记忆持久化 =====
        memory.append(userId, SESSION_ID, userMessage, draft, tokens);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("answer", draft);
        out.put("rounds", rounds);
        out.put("usedModel", polished ? usedModel + "+polish" : usedModel);
        out.put("toolTrace", toolTrace);
        out.put("tokensUsed", tokens);
        return out;
    }

    /** 清空当前用户的对话记忆 */
    public void clear(Long userId) {
        memory.clear(userId, SESSION_ID);
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
