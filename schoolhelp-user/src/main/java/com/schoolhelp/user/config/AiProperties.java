package com.schoolhelp.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
    private String model = "glm-4-flash";

    /** 视觉模型（课表截图识别用，智谱永久免费） */
    private String visionModel = "glm-4v-flash";

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
