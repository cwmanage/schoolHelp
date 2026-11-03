package com.schoolhelp.user.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.schoolhelp.user.entity.AgentChatMessage;
import com.schoolhelp.user.mapper.AgentChatMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 对话记忆持久化：按消息行存 MySQL（跨设备、可查询、可清理）。
 * - session 维度隔离（当前固定 "default"，未来多会话零迁移）
 * - append：每轮对话结束后存 user + assistant 两条（含 token 消耗）
 * - loadRecent：取最近 N 条（正序），供 Agent 循环作上下文
 * - clear：用户清空对话时调用
 * 超长控制已由「只取最近 N 条 + 单条截断」实现；token 级压缩为后续增强项（ContextManager 钩子）。
 */
@Service
@RequiredArgsConstructor
public class ChatMemoryService {

    private static final int MAX_LOAD = 8;
    /** 单条消息上限（防异常超长撑爆上下文），超出截断 */
    private static final int MAX_CONTENT_LEN = 4000;
    /** 记忆保留天数 */
    private static final int KEEP_DAYS = 30;

    private final AgentChatMessageMapper mapper;

    /** 读取某会话最近 MAX_LOAD 条（正序返回：旧 -> 新） */
    public List<AgentChatMessage> loadRecent(Long userId, String sessionId) {
        List<AgentChatMessage> list = mapper.selectList(new LambdaQueryWrapper<AgentChatMessage>()
                .eq(AgentChatMessage::getUserId, userId)
                .eq(AgentChatMessage::getSessionId, sessionId)
                .orderByDesc(AgentChatMessage::getId)
                .last("LIMIT " + MAX_LOAD));
        Collections.reverse(list);   // 查询为倒序取最新 N 条，翻转为时间正序
        return list;
    }

    /** 追加一轮对话（user + assistant 各一条；tokens 为本轮 LLM 消耗，可 0） */
    public void append(Long userId, String sessionId, String userContent, String assistantContent, long tokens) {
        insert(userId, sessionId, "user", userContent, 0);
        if (assistantContent != null && !assistantContent.isBlank()) {
            insert(userId, sessionId, "assistant", assistantContent, tokens);
        }
    }

    /** 清空某用户某会话的记忆 */
    public void clear(Long userId, String sessionId) {
        mapper.delete(new LambdaQueryWrapper<AgentChatMessage>()
                .eq(AgentChatMessage::getUserId, userId)
                .eq(AgentChatMessage::getSessionId, sessionId));
    }

    private void insert(Long userId, String sessionId, String role, String content, long tokens) {
        if (content == null || content.isBlank()) return;
        AgentChatMessage m = new AgentChatMessage();
        m.setUserId(userId);
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content.length() > MAX_CONTENT_LEN ? content.substring(0, MAX_CONTENT_LEN) : content);
        m.setTokensUsed(tokens);
        m.setCreatedAt(LocalDateTime.now());
        mapper.insert(m);
        // 简易清理：顺手删除超出保留期的旧记录
        mapper.delete(new LambdaQueryWrapper<AgentChatMessage>()
                .eq(AgentChatMessage::getUserId, userId)
                .lt(AgentChatMessage::getCreatedAt, LocalDateTime.now().minusDays(KEEP_DAYS)));
    }
}
