package com.schoolhelp.user.controller;

import com.schoolhelp.common.result.Result;
import com.schoolhelp.common.util.UserContext;
import com.schoolhelp.user.dto.AiChatDTO;
import com.schoolhelp.user.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * AI 问答（走网关 JWT 鉴权，登录后可用；网关前缀 /api/user，转发后为 /ai/chat）
 */
@RestController
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /** 对话：携带最近几轮历史，返回 AI 回答 */
    @PostMapping("/ai/chat")
    public Result<Map<String, Object>> chat(@Valid @RequestBody AiChatDTO dto) {
        return Result.ok(aiService.chat(UserContext.getUserId(), dto));
    }

    /** 课表截图识别：返回结构化课程条目，前端确认后逐条导入 */
    @PostMapping("/ai/schedule-ocr")
    public Result<List<Map<String, Object>>> scheduleOcr(@RequestParam("file") MultipartFile file) {
        return Result.ok(aiService.scheduleOcr(UserContext.getUserId(), file));
    }

    /** AI 作息建议：按每天节数生成每节课起止时间（非法自动回退默认模板） */
    @PostMapping("/ai/time-suggest")
    public Result<List<Map<String, Object>>> timeSuggest(@RequestParam("sections") Integer sections) {
        return Result.ok(aiService.timeSuggest(UserContext.getUserId(), sections));
    }
}
