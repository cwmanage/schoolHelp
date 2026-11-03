package com.schoolhelp.user.agent;

import lombok.Data;

/**
 * 模型档案（ModelProfile）：一个可调用端点的完整能力卡片。
 * 所有「模型异构」差异（窗口/思考分级/档位/模态/有效期）收敛在这里，
 * 编排层按 tier 选型、LlmClient 按档案组装请求、ContextManager 按窗口裁剪。
 * 新增模型 = yml 加一段 profile，代码零改动。
 */
@Data
public class ModelProfile {

    /** 端点唯一 ID（如 main-4.7-flash），同时是 Semaphore/熔断表的键 */
    private String id;

    /** 所属 Key：main=主 Key / trial=试用 Key（决定用哪个 API Key） */
    private String keyRef = "main";

    /** 模型编码（API 的 model 参数） */
    private String model;

    /** 上下文窗口（tokens）——ContextManager 按它做裁剪/压缩决策 */
    private long contextTokens = 128000;

    /** 档位：FLAGSHIP（旗舰，最终回答/精加工） / LITE（轻量，工具决策/高频） / VISION（视觉） */
    private String tier = "LITE";

    /** 思考分级：none=不支持 / optional=可开关(thinking.type) / forced=强制思考 */
    private String thinking = "none";

    /** 是否支持工具调用（function calling） */
    private boolean tools = true;

    /** 是否支持视觉输入（图片 image_url） */
    private boolean vision = false;

    /** 资源包有效期（yyyy-MM-dd）；留空=永不过期，到期自动踢出 */
    private String expireAt = "";
}
