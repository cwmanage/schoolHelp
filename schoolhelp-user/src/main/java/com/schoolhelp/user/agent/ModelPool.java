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
 * 模型并发池 v2（降级链）：智谱免费档每模型 1 并发（占用式，响应结束释放）；
 * 资源包（付费档）并发更高。请求按「降级链」顺序尝试：
 *   免费 agent 模型 -> 资源包模型（按配置顺序，过滤已过期）-> 报错
 * 每个模型独立 Semaphore(1)；资源包带 expireAt，到期自动踢出链。
 */
@Slf4j
@Service
public class ModelPool {

    private final AiProperties props;
    private final Map<String, Semaphore> pools = new ConcurrentHashMap<>();

    public ModelPool(AiProperties props) {
        this.props = props;
    }

    /** 当前有效降级链（免费主模型在前，过滤过期资源包） */
    public List<String> chain() {
        List<String> chain = new ArrayList<>();
        chain.add(props.getAgentModel());
        for (AiProperties.FallbackModel fm : props.getAgentFallbackModels()) {
            if (fm.getModel() == null || fm.getModel().isBlank()) continue;
            if (expired(fm.getExpireAt())) {
                log.info("模型池跳过已过期资源包: {} (到期 {})", fm.getModel(), fm.getExpireAt());
                continue;
            }
            if (!chain.contains(fm.getModel())) chain.add(fm.getModel());
        }
        return chain;
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
