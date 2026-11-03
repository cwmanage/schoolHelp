package com.schoolhelp.user.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.schoolhelp.user.entity.AgentChatMessage;
import com.schoolhelp.user.mapper.AgentChatMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话记忆持久化：按消息行存 MySQL（跨设备、可查询、可清理）。
 * - append：每轮对话结束后存 user + assistant 两条
 * - loadRecent：取最近 N 条（默认 8 条 = 4 轮），供 Agent 循环作上下文
 * - clear：用户清空对话时调用
 * 超长自动截断策略已由「只取最近 N 条」实现；token 级压缩为后续增强项。
 */
@Service
@RequiredArgsConstructor
public class ChatMemoryService {

    private static final int MAX_LOAD = 8;
    /** 单条消息上限（防异常超长撑爆上下文），超出截断 */
    private static final int MAX_CONTENT_LEN = 4000;
    /** 记忆保留天数（过期自动清理入口，当前由 loadRecent 的 N 条限制兜底） */
    private static final int KEEP_DAYS = 30;

    private final AgentChatMessageMapper mapper;

    /** 读取最近 maxLoad 条（正序返回：旧 -> 新） */
    public List<AgentChatMessage> loadRecent(Long userId) {
        List<AgentChatMessage> list = mapper.selectList(new LambdaQueryWrapper<AgentChatMessage>()
                .eq(AgentChatMessage::getUserId, userId)
                .orderByDesc(AgentChatMessage::getId)
                .last("LIMIT " + MAX_LOAD));
        java.util.Collections.reverse(list);   // 查询为倒序取最新 N 条，翻转为时间正序
        return list;
    }

    /** 追加一轮对话（user + assistant 各一条） */
    public void append(Long userId, String userContent, String assistantContent) {
        insert(userId, "user", userContent);
        if (assistantContent != null && !assistantContent.isBlank()) {
            insert(userId, "assistant", assistantContent);
        }
    }

    /** 清空某用户的记忆 */
    public void clear(Long userId) {
        mapper.delete(new LambdaQueryWrapper<AgentChatMessage>()
                .eq(AgentChatMessage::getUserId, userId));
    }

    private void insert(Long userId, String role, String content) {
        if (content == null || content.isBlank()) return;
        AgentChatMessage m = new AgentChatMessage();
        m.setUserId(userId);
        m.setRole(role);
        m.setContent(content.length() > MAX_CONTENT_LEN ? content.substring(0, MAX_CONTENT_LEN) : content);
        m.setCreatedAt(LocalDateTime.now());
        mapper.insert(m);
        // 简易清理：顺手删除超出保留期的旧记录（低频写入口，无性能压力）
        mapper.delete(new LambdaQueryWrapper<AgentChatMessage>()
                .eq(AgentChatMessage::getUserId, userId)
                .lt(AgentChatMessage::getCreatedAt, LocalDateTime.now().minusDays(KEEP_DAYS)));
    }
}
