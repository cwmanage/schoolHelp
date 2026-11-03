package com.schoolhelp.user.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolhelp.common.exception.BusinessException;
import com.schoolhelp.user.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 调用适配层：按 ModelProfile 组装请求，收敛「每模型差异」。
 *
 * 职责边界（架构契约）：
 * 1. thinking 分级：按 profile.thinking 组装参数（optional→disabled / none→不传 / forced→enabled）
 * 2. 单端点读超时 45s：高峰期限流响应极慢，超时即视为该端点不可用（快速切换）
 * 3. 【SSE 改造契约】降级决策必须在「首 token 之前」完成——本层先完成端点选择与降级，
 *    流式只是最终端点的传输方式；未来 SSE 化时不得把降级逻辑移到流中。
 * 4. 【内容审计出口】所有模型输出经本层返回——未来内容过滤/审计在此统一插入。
 */
@Slf4j
@Component
public class LlmClient {

    private final AiProperties props;
    private final RestClient restClient;

    public LlmClient(AiProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(10));
        // 单端点读超时 45s：高峰期限流响应极慢（30-70s 才返回 429），超时即视为该端点不可用快速切换，
        // 保证整个降级链总时长可控（不熬穿 nginx 180s）
        f.setReadTimeout(Duration.ofSeconds(45));
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(f)
                .build();
    }

    /**
     * 调用 LLM（单次，无内部重试）：任何网络/限流异常都抛 ModelBusyException 触发降级链。
     * 1305/1302 限流在高峰期响应极慢且变体多（429/1305/1302/非JSON错误体），
     * 内部重试只会加剧 Key 级 RPM 限流——正确解法是「立即切换下一个端点」（不同 Key/模型的限速独立）。
     *
     * @param ep       端点（含 apiKey）
     * @param thinking 是否开启深度思考（仅 profile.thinking=optional 的端点生效）
     * @param tools    工具定义；null 表示本次不携带工具（非 agent 场景）
     * @return OpenAI 兼容响应（含 choices / usage）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(ModelPool.LlmEndpoint ep, List<Map<String, Object>> messages,
                                    boolean thinking, List<Map<String, Object>> tools) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", ep.model());
        body.put("messages", messages);
        body.put("stream", false);
        body.put("max_tokens", 2048);
        body.put("temperature", 0.3);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        // 思考分级：仅 optional 档端点响应开关（none 档不支持、forced 档由调用方传 true）
        if (thinking && "optional".equals(props.profileById(ep.id()).getThinking())) {
            body.put("thinking", Map.of("type", "enabled"));
        } else if (!thinking && "optional".equals(props.profileById(ep.id()).getThinking())) {
            body.put("thinking", Map.of("type", "disabled"));
        }

        try {
            Map<String, Object> resp = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + ep.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (resp != null) return resp;
            throw new ModelBusyException();
        } catch (RestClientException e) {
            String msg = String.valueOf(e.getMessage());
            log.warn("LLM[{}] 调用失败: {}", ep.id(), msg);
            // 认证类错误：换模型/Key 也无意义（同一配置错误），直接报错
            if (msg.contains("401") || msg.contains("403")) {
                throw new BusinessException("AI 服务认证失败，请联系管理员检查 API Key");
            }
            throw new ModelBusyException();   // 其余（429/1305/1302/1113/解析失败/超时等）全部降级
        }
    }

    /** 从 OpenAI 兼容响应提取 usage 总 token（无则 0）——成本可观测 */
    @SuppressWarnings("unchecked")
    public long extractTokens(Map<String, Object> resp) {
        try {
            Map<String, Object> usage = (Map<String, Object>) resp.get("usage");
            if (usage == null) return 0;
            Object total = usage.get("total_tokens");
            return total instanceof Number n ? n.longValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
