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
 * 模型并发池 v2（降级链 + 熔断）：智谱免费档每模型 1 并发（占用式，响应结束释放）；
 * 资源包（付费档）并发更高。请求按「降级链」顺序尝试：
 *   免费 agent 模型 -> 资源包模型（按配置顺序，过滤已过期）-> 报错
 * 每个模型独立 Semaphore(1)；资源包带 expireAt，到期自动踢出链。
 * 熔断：某模型连续限流（1305/1302）后 60 秒内直接跳过——高峰期自动「整体降级」到资源包模型，
 *       避免每个请求都在免费模型上白耗一次慢响应。
 */
@Slf4j
@Service
public class ModelPool {

    private final AiProperties props;
    private final Map<String, Semaphore> pools = new ConcurrentHashMap<>();
    /** 熔断表：模型 -> 熔断截止时间戳(ms)；熔断期内请求直接跳过该模型 */
    private final Map<String, Long> throttleUntil = new ConcurrentHashMap<>();
    private static final long THROTTLE_MS = 60_000;

    public ModelPool(AiProperties props) {
        this.props = props;
    }

    /** 当前有效降级链（免费主模型在前；过滤过期资源包与熔断中的模型；全熔断时保底返回未熔断的首个） */
    public List<String> chain() {
        List<String> chain = new ArrayList<>();
        List<String> throttled = new ArrayList<>();
        chain.add(props.getAgentModel());
        for (AiProperties.FallbackModel fm : props.getAgentFallbackModels()) {
            if (fm.getModel() == null || fm.getModel().isBlank()) continue;
            if (expired(fm.getExpireAt())) {
                log.info("模型池跳过已过期资源包: {} (到期 {})", fm.getModel(), fm.getExpireAt());
                continue;
            }
            if (!chain.contains(fm.getModel())) chain.add(fm.getModel());
        }
        // 熔断过滤：主模型也被熔断时保底保留（避免空链），降级模型直接跳过
        long now = System.currentTimeMillis();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < chain.size(); i++) {
            Long until = throttleUntil.get(chain.get(i));
            boolean inThrottle = until != null && until > now;
            if (!inThrottle) {
                result.add(chain.get(i));
            } else if (i == 0 && result.isEmpty()) {
                result.add(chain.get(i));   // 主模型熔断：仍保留在链尾兜底（万一已恢复）
            } else {
                throttled.add(chain.get(i) + "(熔断中)");
            }
        }
        if (!throttled.isEmpty()) {
            log.info("模型池熔断跳过: {}", throttled);
        }
        return result;
    }

    /** 标记模型限流（触发熔断：THROTTLE_MS 内跳过） */
    public void markThrottled(String model) {
        throttleUntil.put(model, System.currentTimeMillis() + THROTTLE_MS);
        log.warn("模型 {} 触发熔断，{} 秒内跳过", model, THROTTLE_MS / 1000);
    }

    /** 立即尝试占用 */
    public boolean tryAcquire(String model) {
        return pool(model).tryAcquire();
    }

    /** 带等待的占用 */
    public boolean tryAcquire(String model, int timeoutSeconds) throws InterruptedException {
        return pool(model).tryAcquire(timeoutSeconds, TimeUnit.SECONDS);
    }

    /** 释放（响应结束后 finally 调用） */
    public void release(String model) {
        pool(model).release();
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

    private Semaphore pool(String model) {
        return pools.computeIfAbsent(model, k -> new Semaphore(1));
    }
}
