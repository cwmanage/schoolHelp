package com.schoolhelp.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolhelp.common.exception.BusinessException;
import com.schoolhelp.user.config.AiProperties;
import com.schoolhelp.user.dto.AiChatDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 问答：代理转发免费大模型（OpenAI 兼容接口，默认智谱 GLM-4-Flash）
 * - 简单限流：每用户每分钟 N 次（内存窗口，重启清零）
 * - 服务端注入 system prompt，前端只传最近几轮 user/assistant 消息
 */
@Slf4j
@Service
public class AiService {

    private static final String SYSTEM_PROMPT = "你是校园学习助手「课屿」的 AI 助教，服务于大学生的课程学习与校园生活。"
            + "回答要求：1) 使用简体中文，简洁准确，重点突出；2) 涉及学习问题给出可操作的思路与步骤；"
            + "3) 不讨论政治敏感、违法违规内容；4) 不确定的事情明确说明，不编造。";

    private final AiProperties props;
    private final ScheduleTimeService timeService;
    private final RestClient restClient;

    /** 限流窗口：userId -> (窗口起始秒, 已用次数) */
    private final ConcurrentHashMap<Long, long[]> rateWindows = new ConcurrentHashMap<>();

    public AiService(AiProperties props, ScheduleTimeService timeService) {
        this.props = props;
        this.timeService = timeService;
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .build();
    }

    /** 返回 {reply, model} */
    public Map<String, Object> chat(Long userId, AiChatDTO dto) {
        if (!props.isConfigured()) {
            throw new BusinessException("AI 功能未配置（缺少 API Key），请联系管理员");
        }
        checkRateLimit(userId);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        for (AiChatDTO.Message m : dto.getMessages()) {
            messages.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getModel());
        body.put("messages", messages);
        body.put("stream", false);
        // 控制回答长度与发散度，适合快问快答
        body.put("max_tokens", 1024);
        body.put("temperature", 0.7);

        Map<String, Object> resp;
        try {
            resp = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            log.warn("AI 调用失败: {}", e.getMessage());
            throw new BusinessException("AI 服务暂时不可用，请稍后再试");
        }

        if (resp == null) {
            throw new BusinessException("AI 服务返回为空，请稍后再试");
        }
        String reply = extractReply(resp);
        Map<String, Object> out = new HashMap<>();
        out.put("reply", reply);
        out.put("model", props.getModel());
        return out;
    }

    /** 解析 OpenAI 兼容响应：choices[0].message.content */
    @SuppressWarnings("unchecked")
    private String extractReply(Map<String, Object> resp) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BusinessException("AI 未返回回答，请稍后再试");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            Object content = message == null ? null : message.get("content");
            if (content == null || content.toString().isBlank()) {
                throw new BusinessException("AI 未返回回答，请稍后再试");
            }
            return content.toString();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 响应解析失败: {}", e.getMessage());
            throw new BusinessException("AI 响应异常，请稍后再试");
        }
    }

    private void checkRateLimit(Long userId) {
        long nowSec = System.currentTimeMillis() / 1000;
        long[] window = rateWindows.compute(userId, (k, v) -> {
            if (v == null || nowSec - v[0] >= 60) {
                return new long[]{nowSec, 0};
            }
            return v;
        });
        if (window[1] >= props.getRateLimitPerMinute()) {
            throw new BusinessException("提问太频繁啦，请稍后再试（每分钟 " + props.getRateLimitPerMinute() + " 次）");
        }
        window[1]++;
    }

    // ==================== 课表截图识别 ====================

    private static final String SCHEDULE_OCR_PROMPT = "你是课表识别助手。识别这张课程表截图中的所有课程条目，"
            + "输出 JSON 数组，每个元素格式：\n"
            + "{\"courseName\":\"课程名\",\"weekDay\":1,\"startSection\":1,\"endSection\":2,"
            + "\"room\":\"教室\",\"teacher\":\"教师\",\"weekType\":0,\"weeks\":\"1-16\"}\n"
            + "字段说明：weekDay 取 1-7（1=周一…7=周日）；startSection/endSection 取 1-12 节；"
            + "weekType 取 0(每周)/1(单周)/2(双周)；weeks 如 1-16 或 1,3,5-16。\n"
            + "要求：\n"
            + "1. 只输出 JSON 数组本体，禁止任何解释文字、禁止 markdown 代码块标记\n"
            + "2. 同一门课有多个时间段（如每周上两次）输出多条\n"
            + "3. 连堂课输出正确的 startSection 到 endSection\n"
            + "4. 无法确定的字段：文本字段用空串，数字字段 weekDay 用 1、节次用 1、weekType 用 0\n"
            + "5. 图片不是课程表时输出 []";

    /** 识别课表截图，返回课程条目列表（字段与 ScheduleDTO 对齐，前端确认后逐条导入） */
    public List<Map<String, Object>> scheduleOcr(Long userId, MultipartFile file) {
        if (!props.isConfigured()) {
            throw new BusinessException("AI 功能未配置（缺少 API Key），请联系管理员");
        }
        checkRateLimit(userId);
        validateImage(file);

        byte[] imageBytes;
        try {
            imageBytes = file.getBytes();
        } catch (java.io.IOException e) {
            log.warn("读取上传图片失败: {}", e.getMessage());
            throw new BusinessException("读取图片失败，请重试");
        }
        String dataUrl = "data:" + file.getContentType() + ";base64,"
                + Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "image_url");
        content.put("image_url", Map.of("url", dataUrl));
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", SCHEDULE_OCR_PROMPT);

        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getVisionModel());
        body.put("messages", List.of(Map.of("role", "user", "content", List.of(content, textPart))));
        body.put("stream", false);
        body.put("max_tokens", 1024); // 智谱 GLM-4V-Flash 上限 1024
        body.put("temperature", 0.1);

        Map<String, Object> resp;
        try {
            resp = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            log.warn("课表识别调用失败: {}", e.getMessage());
            throw new BusinessException("AI 服务暂时不可用，请稍后再试");
        }
        if (resp == null) {
            throw new BusinessException("AI 服务返回为空，请稍后再试");
        }
        String reply = extractReply(resp);
        return parseScheduleItems(reply);
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择图片");
        }
        if (file.getSize() > props.getMaxImageMb() * 1024L * 1024L) {
            throw new BusinessException("图片不能超过 " + props.getMaxImageMb() + "MB");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw new BusinessException("仅支持图片文件（jpg/png/webp）");
        }
    }

    /** 从回复中提取 JSON 数组并规范化字段 */
    private List<Map<String, Object>> parseScheduleItems(String reply) {
        String json = extractJsonArray(reply);
        if (json == null) {
            throw new BusinessException("未能从图片中识别出课程，请换一张更清晰的课表截图");
        }
        List<Map<String, Object>> raw;
        try {
            raw = new ObjectMapper().readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("课表识别结果解析失败: {}", e.getMessage());
            throw new BusinessException("识别结果异常，请重试一次");
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            String name = str(item.get("courseName"));
            if (name.isEmpty()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseName", name);
            m.put("weekDay", clamp(intOf(item.get("weekDay"), 1), 1, 7));
            m.put("startSection", clamp(intOf(item.get("startSection"), 1), 1, 12));
            m.put("endSection", clamp(intOf(item.get("endSection"), 1), 1, 12));
            m.put("room", str(item.get("room")));
            m.put("teacher", str(item.get("teacher")));
            m.put("weekType", clamp(intOf(item.get("weekType"), 0), 0, 2));
            m.put("weeks", str(item.get("weeks")));
            out.add(m);
        }
        if (out.isEmpty()) {
            throw new BusinessException("未能从图片中识别出课程，请换一张更清晰的课表截图");
        }
        return out;
    }

    /** 提取回复文本中第一个 [ 到最后一个 ] 之间的 JSON（兼容模型偶尔带 markdown 标记） */
    private String extractJsonArray(String text) {
        if (text == null) return null;
        text = text.replace("```json", "```").replace("```", "").trim();
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) return null;
        return text.substring(start, end + 1);
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private int intOf(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ==================== AI 作息建议 ====================

    /** AI 生成 N 节作息建议（解析与校验在 ScheduleTimeService，非法自动回退默认模板前 N 节） */
    public List<Map<String, Object>> timeSuggest(Long userId, Integer sections) {
        if (!props.isConfigured()) {
            throw new BusinessException("AI 功能未配置（缺少 API Key），请联系管理员");
        }
        if (sections == null || sections < 4 || sections > 12) {
            throw new BusinessException(400, "节数范围 4-12");
        }
        checkRateLimit(userId);

        String prompt = "你是大学课程表作息安排助手。学校每天共 " + sections + " 节课，请给出每节课的起止时间（24小时制 HH:mm）。\n"
                + "要求：\n"
                + "1. 上午从 8:00 左右开始；每节课 40-50 分钟，节间休息 10-20 分钟\n"
                + "2. 中午 11:40-14:00 左右安排午休、17:40-19:00 左右安排晚餐（若该范围有节次则相应跨越顺延）\n"
                + "3. 只输出 JSON 数组本体：[{\"section\":1,\"start\":\"08:00\",\"end\":\"08:45\"},...]，"
                + "section 从 1 连续到 " + sections + "，禁止任何解释文字、禁止 markdown 代码块标记";

        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getModel());
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        body.put("stream", false);
        body.put("max_tokens", 1024);
        body.put("temperature", 0.3);

        Map<String, Object> resp;
        try {
            resp = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            log.warn("AI 作息建议调用失败: {}", e.getMessage());
            throw new BusinessException("AI 服务暂时不可用，请稍后再试");
        }
        if (resp == null) {
            throw new BusinessException("AI 服务返回为空，请稍后再试");
        }
        String reply = extractReply(resp);
        return timeService.aiSuggest(userId, sections, reply);
    }
}
