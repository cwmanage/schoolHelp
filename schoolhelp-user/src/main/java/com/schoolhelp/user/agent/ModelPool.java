package com.schoolhelp.user.agent;

import com.schoolhelp.user.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 模型并发池 v3（多 Key 降级链 + 熔断）：
 * 降级链条目 = {所属Key, 模型}，每个条目独立 Semaphore(1)（智谱限流按「Key×模型」独立计）。
 * 链：主 Key 免费模型 → 试用 Key 资源包模型（1305/1302 时按序切换）。
 * 熔断：条目限流后 60 秒内跳过——高峰期自动「整体降级」到下一个 Key 的模型。
 * 过期：条目带 expireAt（资源包有效期），到期自动踢出链。
 */
@Slf4j
@Service
public class ModelPool {

    private final AiProperties props;
    /** 端点(keyRef|model) -> Semaphore(1) */
    private final Map<String, Semaphore> pools = new ConcurrentHashMap<>();
    /** 熔断表：端点 -> 熔断截止时间戳(ms) */
    private final Map<String, Long> throttleUntil = new ConcurrentHashMap<>();
    private static final long THROTTLE_MS = 300_000;   // 5 分钟：高峰期免费池不可用持续较久，长熔断避免每个请求白撞慢响应

    public ModelPool(AiProperties props) {
        this.props = props;
    }

    /** LLM 调用端点：一个 API Key + 一个模型构成独立限速单元 */
    public record LlmEndpoint(String id, String model, String apiKey) {}

    /** 当前有效降级链（文本 Agent 链） */
    public List<LlmEndpoint> chain() {
        return chain(props.getAgentModel(), props.getAgentFallbackModels());
    }

    /** 通用降级链构建（主模型 + 回退列表；过滤过期资源包与熔断中的端点）——文本/视觉链共用 */
    public List<LlmEndpoint> chain(String primaryModel, List<AiProperties.FallbackModel> fallbacks) {
        List<LlmEndpoint> chain = new ArrayList<>();
        chain.add(new LlmEndpoint("main|" + primaryModel,
                primaryModel, props.getApiKey()));
        for (AiProperties.FallbackModel fm : fallbacks) {
            if (fm.getModel() == null || fm.getModel().isBlank()) continue;
            if (expired(fm.getExpireAt())) {
                log.info("模型池跳过已过期资源包: {} (到期 {})", fm.getModel(), fm.getExpireAt());
                continue;
            }
            String apiKey = resolveKey(fm.getKeyRef());
            if (apiKey == null || apiKey.isBlank()) {
                log.info("模型池跳过无 Key 的端点: {} (keyRef={})", fm.getModel(), fm.getKeyRef());
                continue;
            }
            LlmEndpoint ep = new LlmEndpoint(fm.getKeyRef() + "|" + fm.getModel(), fm.getModel(), apiKey);
            if (chain.stream().noneMatch(e -> e.id().equals(ep.id()))) chain.add(ep);
        }

        // 熔断过滤：主端点熔断时保底保留在链尾（万一已恢复），其余跳过
        long now = System.currentTimeMillis();
        List<LlmEndpoint> result = new ArrayList<>();
        List<String> throttled = new ArrayList<>();
        for (int i = 0; i < chain.size(); i++) {
            LlmEndpoint ep = chain.get(i);
            Long until = throttleUntil.get(ep.id());
            boolean inThrottle = until != null && until > now;
            if (!inThrottle) {
                result.add(ep);
            } else if (i == 0) {
                result.add(ep);   // 主端点熔断仍兜底保留（全链熔断时不至于无模型可试）
            } else {
                throttled.add(ep.model() + "(熔断中)");
            }
        }
        if (!throttled.isEmpty()) log.info("模型池熔断跳过: {}", throttled);
        return result;
    }

    private String resolveKey(String keyRef) {
        if ("trial".equals(keyRef)) return props.getTrialApiKey();
        return props.getApiKey();   // main / 未识别一律用主 Key
    }

    /** 立即尝试占用 */
    public boolean tryAcquire(LlmEndpoint ep) {
        return pool(ep.id()).tryAcquire();
    }

    /** 带等待的占用 */
    public boolean tryAcquire(LlmEndpoint ep, int timeoutSeconds) throws InterruptedException {
        return pool(ep.id()).tryAcquire(timeoutSeconds, TimeUnit.SECONDS);
    }

    /** 释放（响应结束后 finally 调用） */
    public void release(LlmEndpoint ep) {
        pool(ep.id()).release();
    }

    /** 标记限流熔断（THROTTLE_MS 内跳过该端点） */
    public void markThrottled(LlmEndpoint ep) {
        throttleUntil.put(ep.id(), System.currentTimeMillis() + THROTTLE_MS);
        log.warn("端点 {} 触发熔断，{} 秒内跳过", ep.id(), THROTTLE_MS / 1000);
    }

    private boolean expired(String expireAt) {
        if (expireAt == null || expireAt.isBlank()) return false;
        try {
            return LocalDate.parse(expireAt).isBefore(LocalDate.now());
        } catch (DateTimeParseException e) {
            log.warn("资源包过期日期格式错误（应为 yyyy-MM-dd）: {}", expireAt);
            return false;   // 配置错误时宁可用不踢出
        }
    }

    private Semaphore pool(String endpointId) {
        return pools.computeIfAbsent(endpointId, k -> new Semaphore(1));
    }
}
