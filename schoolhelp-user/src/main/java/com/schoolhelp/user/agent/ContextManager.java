package com.schoolhelp.user.agent;

import com.schoolhelp.user.entity.AgentChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 上下文管理：按「目标模型的窗口」做裁剪决策（模型异构的解）。
 * - token 估算：中文约 1 字/token，保守按字符数 × 0.9 估算
 * - 截断：输入总 token 超「窗口 70%」（预留生成空间）时，从最旧的历史消息开始丢弃
 *   （保留 system + 本轮提问 + 尽量多的近期历史）
 * - 压缩钩子（Phase B）：截断前可插入「LLM 摘要压缩」——历史摘要替代原文
 */
@Component
public class ContextManager {

    /** 生成预留比例：输入最多占窗口的 70% */
    private static final double INPUT_BUDGET_RATIO = 0.7;

    /** 估算一段文本的 token 数（中文保守高估） */
    public long estimateTokens(String text) {
        return text == null ? 0 : (long) (text.length() * 0.9);
    }

    /**
     * 按目标模型窗口裁剪消息列表。
     * messages 约定：[0]=system，之后为历史(user/assistant)+本轮 user。
     * 超预算时从「历史区」最旧的消息开始丢弃（system 与本轮 user 永不丢弃）。
     */
    public List<Map<String, Object>> truncateForProfile(List<Map<String, Object>> messages, ModelProfile profile) {
        long window = profile == null ? 128000 : profile.getContextTokens();
        long budget = (long) (window * INPUT_BUDGET_RATIO);

        // 先算总量；不超预算直接原样返回
        long total = 0;
        for (Map<String, Object> m : messages) {
            total += estimateTokens(String.valueOf(m.get("content")));
        }
        if (total <= budget) return messages;

        // 超预算：[0]=system 必留，末条（本轮 user）必留，中间历史从最旧开始丢
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(messages.get(0));
        long kept = estimateTokens(String.valueOf(messages.get(0).get("content")))
                + estimateTokens(String.valueOf(messages.get(messages.size() - 1).get("content")));
        // 从倒数第二条往前保留（跳过末条，末条已计入）
        for (int i = messages.size() - 2; i >= 1; i--) {
            long t = estimateTokens(String.valueOf(messages.get(i).get("content")));
            if (kept + t > budget) break;
            kept += t;
            out.add(1 + out.size() - 1, messages.get(i));   // 插到 system 之后，保持时间顺序
        }
        out.add(messages.get(messages.size() - 1));
        return out;
    }

    /** AgentChatMessage -> OpenAI 消息格式（记忆回放用，带发送时间标注——防历史相对时间漂移） */
    public Map<String, Object> toMessage(AgentChatMessage m) {
        String ts = m.getCreatedAt() == null ? ""
                : "[" + m.getCreatedAt().toLocalDate() + " "
                + (m.getCreatedAt().toLocalTime().toString().length() >= 5
                        ? m.getCreatedAt().toLocalTime().toString().substring(0, 5)
                        : m.getCreatedAt().toLocalTime().toString()) + "] ";
        return Map.of("role", m.getRole(), "content", ts + m.getContent());
    }
}
