package com.schoolhelp.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 问答配置（免费大模型 OpenAI 兼容接口，默认智谱 GLM）
 */
@Data
@Component
@ConfigurationProperties(prefix = "schoolhelp.ai")
public class AiProperties {

    /** OpenAI 兼容接口基础地址（不含 /chat/completions） */
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";

    /** API Key；为空时 AI 功能不可用（前端给出未配置提示） */
    private String apiKey = "";

    /** 模型名 */
    private String model = "GLM-4.7-Flash";

    /** Agent 专用模型（工具调用能力更强；与普通问答模型分池，互不占用并发配额） */
    private String agentModel = "glm-4.7-flash";

    /**
     * Agent 降级链：免费模型 1305/429 时按顺序切到资源包模型（顺序即优先级）。
     * expireAt 为资源包有效期（智谱控制台-资源包页面查看）；到期后自动踢出降级链。
     */
    private List<FallbackModel> agentFallbackModels = new java.util.ArrayList<>();

    @Data
    public static class FallbackModel {
        /** 模型编码（如 glm-4.7 / glm-4.5-air） */
        private String model;
        /** 资源包有效期；留空表示永不过期 */
        private String expireAt;
    }

    /** 视觉模型（课表截图识别用，智谱永久免费） */
    private String visionModel = "GLM-4.6V-Flash";

    /** 识图图片大小上限（MB） */
    private int maxImageMb = 5;

    /** 请求超时（秒） */
    private int timeoutSeconds = 50;

    /** 每用户每分钟最大请求次数 */
    private int rateLimitPerMinute = 5;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
