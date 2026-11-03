package com.schoolhelp.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Agent 对话请求体：
 * - message：本轮用户提问（必填）
 * - history：最近几轮对话上下文（user/assistant，不含本轮；可空）
 *   前端传最近 CONTEXT_COUNT 条，后端再钳制上限，防止超长
 */
@Data
public class AgentChatDTO {

    @NotBlank(message = "提问内容不能为空")
    @Size(max = 500, message = "提问内容不能超过 500 字")
    private String message;

    @Valid
    private List<Msg> history;

    @Data
    public static class Msg {
        /** user / assistant */
        private String role;
        private String content;
    }
}
