package com.schoolhelp.user.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 模型并发池：智谱免费档每个模型只有 1 个并发（占用式——请求发出到响应结束期间配额被占）。
 * 每个模型名一个 Semaphore(1)：
 * - tryAcquire() 立即探测：空闲就用，忙则调用方可选择「排队等待」或「降级到其他模型」
 * - release() 在响应结束后调用——天然实现「响应结束才释放配额」
 * 按模型名隔离：agent 的 glm-4.7-flash 与普通问答的 glm-4-flash 互不占用。
 */
@Slf4j
@Component
public class ModelPool {

    private final Map<String, Semaphore> pools = new ConcurrentHashMap<>();

    /** 立即尝试占用某模型的并发名额 */
    public boolean tryAcquire(String model) {
        return pool(model).tryAcquire();
    }

    /** 带等待的占用：最多等 timeoutSeconds 秒（排队），超时返回 false */
    public boolean tryAcquire(String model, int timeoutSeconds) throws InterruptedException {
        return pool(model).tryAcquire(timeoutSeconds, TimeUnit.SECONDS);
    }

    /** 释放（必须在响应结束后 finally 中调用） */
    public void release(String model) {
        pool(model).release();
    }

    private Semaphore pool(String model) {
        return pools.computeIfAbsent(model, k -> new Semaphore(1));
    }
}
