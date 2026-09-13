package com.schoolhelp.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * AI 对话请求：携带最近几轮历史做上下文
 */
@Data
public class AiChatDTO {

    @NotEmpty(message = "对话内容不能为空")
    @Size(max = 10, message = "历史消息条数超限")
    @Valid
    private List<Message> messages;

    @Data
    public static class Message {

        /** user / assistant（首条必须为 user） */
        @NotBlank
        @Pattern(regexp = "user|assistant", message = "角色不合法")
        private String role;

        @NotBlank(message = "消息内容不能为空")
        @Size(max = 1000, message = "单条消息不能超过 1000 字")
        private String content;
    }
}
