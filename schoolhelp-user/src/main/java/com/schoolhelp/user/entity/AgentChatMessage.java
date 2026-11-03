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

    /** user / assistant */
    private String role;

    private String content;

    private LocalDateTime createdAt;
}
