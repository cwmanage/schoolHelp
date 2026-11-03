package com.schoolhelp.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent 对话请求体（第一版单轮：每次请求独立会话，多轮记忆为路线图第二步）
 */
@Data
public class AgentChatDTO {

    @NotBlank(message = "提问内容不能为空")
    @Size(max = 500, message = "提问内容不能超过 500 字")
    private String message;
}
