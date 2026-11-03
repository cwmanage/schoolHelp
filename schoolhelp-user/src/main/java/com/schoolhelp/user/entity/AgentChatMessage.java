package com.schoolhelp.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Agent 对话记忆（按消息行持久化；大赛"多轮记忆"考点） */
@Data
@TableName("agent_chat_message")
public class AgentChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 会话 ID（当前固定 default，未来多会话） */
    private String sessionId = "default";

    /** user / assistant */
    private String role;

    private String content;

    /** 本轮 LLM 消耗 token（成本可观测） */
    private Long tokensUsed;

    private LocalDateTime createdAt;
}
