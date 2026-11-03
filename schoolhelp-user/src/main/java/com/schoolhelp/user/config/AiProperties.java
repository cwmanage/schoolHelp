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

    /** 试用 API Key（新账号资源包，3 个月有效期；明文经环境变量注入，不落仓库） */
    private String trialApiKey = "";

    /** 识图图片大小上限（MB） */
    private int maxImageMb = 5;

    /** 请求超时（秒） */
    private int timeoutSeconds = 50;

    /** 每用户每分钟最大请求次数 */
    private int rateLimitPerMinute = 5;

    /** Agent 系统提示词（比赛期频繁调优，配置化免发版；%s 占位：日期、星期） */
    private String agentSystemPrompt = "";

    /** 模型档案池（ModelProfile 列表）——Agent/视觉全部端点在此声明 */
    private List<com.schoolhelp.user.agent.ModelProfile> profiles = new java.util.ArrayList<>();

    /** 编排策略：各步骤的模型档位 */
    private Orchestrator orchestrator = new Orchestrator();

    @Data
    public static class Orchestrator {
        /** 工具决策轮档位（结构化输出，LITE 够用且低负载） */
        private String toolDecisionTier = "LITE";
        /** 最终回答轮档位（面向用户的内容，FLAGSHIP 质量） */
        private String finalAnswerTier = "FLAGSHIP";
        /** 决策轮完成工具调用后，是否用 FLAGSHIP 档「精炼」最终回答（多一次调用，质量↑额度↓权衡） */
        private boolean polishWithFlagship = false;
    }

    /** 按 ID 查档案（找不到返回 null） */
    public com.schoolhelp.user.agent.ModelProfile profileById(String id) {
        return profiles.stream()
                .filter(p -> id.equals(p.getId()))
                .findFirst().orElse(null);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
