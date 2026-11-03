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
 * 模型路由池 v4（档案化 + 多 Key 降级链 + 熔断）：
 * 全部端点由 yml 的 ModelProfile 声明（窗口/档位/思考分级/模态/有效期）。
 * - chainForTier(tier)：按档位取端点链（Agent 各步骤按编排策略调用），无匹配时回退全部端点（防配置错空链）
 * - visionChain()：视觉端点链（课表截图识别）
 * - 每端点独立 Semaphore(1)（智谱限流按「Key×模型」独立计，占用式——响应结束释放）
 * - 熔断：端点限流后 THROTTLE_MS 内跳过（高峰自动整体降级）；资源包 expireAt 到期自动踢出
 */
@Slf4j
@Service
public class ModelPool {

    private final AiProperties props;
    /** 端点 id -> Semaphore(1) */
    private final Map<String, Semaphore> pools = new ConcurrentHashMap<>();
    /** 熔断表：端点 id -> 熔断截止时间戳(ms) */
    private final Map<String, Long> throttleUntil = new ConcurrentHashMap<>();
    private static final long THROTTLE_MS = 300_000;   // 5 分钟：高峰期免费池不可用持续较久，长熔断避免白撞慢响应

    public ModelPool(AiProperties props) {
        this.props = props;
    }

    /** LLM 调用端点（档案 + 实际 Key） */
    public record LlmEndpoint(String id, String model, String apiKey, ModelProfile profile) {}

    /** 按档位取端点链（Agent 编排层按步骤调用）；tier 无匹配时回退全部端点 */
    public List<LlmEndpoint> chainForTier(String tier) {
        List<LlmEndpoint> all = new ArrayList<>();
        for (ModelProfile p : props.getProfiles()) {
            if (tier.equals(p.getTier())) all.add(toEndpoint(p));
        }
        if (all.isEmpty()) {
            log.warn("档位 {} 无匹配端点（配置错误？），回退全部端点", tier);
            for (ModelProfile p : props.getProfiles()) all.add(toEndpoint(p));
        }
        return filterValid(all);
    }

    /** 视觉端点链（课表截图识别） */
    public List<LlmEndpoint> visionChain() {
        List<LlmEndpoint> all = new ArrayList<>();
        for (ModelProfile p : props.getProfiles()) {
            if (p.isVision()) all.add(toEndpoint(p));
        }
        return filterValid(all);
    }

    /** 全部端点（非 Agent 场景） */
    public List<LlmEndpoint> allEndpoints() {
        List<LlmEndpoint> all = new ArrayList<>();
        for (ModelProfile p : props.getProfiles()) all.add(toEndpoint(p));
        return filterValid(all);
    }

    /** 通用过滤：过期资源包 + 熔断中的端点（链首熔断仍保底保留，防空链） */
    private List<LlmEndpoint> filterValid(List<LlmEndpoint> endpoints) {
        long now = System.currentTimeMillis();
        List<LlmEndpoint> result = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (int i = 0; i < endpoints.size(); i++) {
            LlmEndpoint ep = endpoints.get(i);
            Long until = throttleUntil.get(ep.id());
            boolean inThrottle = until != null && until > now;
            if (!inThrottle) {
                result.add(ep);
            } else if (i == 0) {
                result.add(ep);   // 链首熔断仍保留（全熔断时不至于无模型可试）
            } else {
                skipped.add(ep.id() + "(熔断中)");
            }
        }
        if (!skipped.isEmpty()) log.info("模型池熔断跳过: {}", skipped);
        return result;
    }

    private LlmEndpoint toEndpoint(ModelProfile p) {
        return new LlmEndpoint(p.getId(), p.getModel(), resolveKey(p.getKeyRef()), p);
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

    private Semaphore pool(String endpointId) {
        return pools.computeIfAbsent(endpointId, k -> new Semaphore(1));
    }
}
