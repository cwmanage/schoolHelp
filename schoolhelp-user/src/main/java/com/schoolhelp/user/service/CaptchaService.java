package com.schoolhelp.user.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码（防爆破）：
 * - Hutool LineCaptcha 生成（4 位字母数字，去除易混淆 0O1lI）
 * - 内存存储（单实例足够），有效期 60s，**一次性消费**（校验即移除）
 * - 懒清理：每次生成时顺手剔除过期条目
 */
@Slf4j
@Service
public class CaptchaService {

    private static final long EXPIRE_MS = 60_000;
    private static final String CHARS = "abcdefghjkmnpqrstuvwxyz23456789";   // 去 0O1lI

    private final SecureRandom random = new SecureRandom();
    /** captchaId -> [code(小写), 过期时间戳ms] */
    private final Map<String, Object[]> store = new ConcurrentHashMap<>();

    /** 生成验证码：返回 {captchaId, image(dataURL)} */
    public Map<String, Object> generate() {
        cleanup();
        String code = randomCode();
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 42, 4, 10);
        captcha.setGenerator(new cn.hutool.captcha.generator.RandomGenerator(CHARS, 4));
        captcha.createCode();
        String realCode = captcha.getCode().toLowerCase();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        store.put(captchaId, new Object[]{realCode, System.currentTimeMillis() + EXPIRE_MS});
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(captcha.getImageBytes());
        return Map.of("captchaId", captchaId, "image", dataUrl, "expireSeconds", EXPIRE_MS / 1000);
    }

    /** 校验（一次性消费，忽略大小写）；失败返回 false */
    public boolean verify(String captchaId, String input) {
        if (captchaId == null || input == null) return false;
        Object[] entry = store.remove(captchaId);   // 无论对错都消费（防重放）
        if (entry == null) return false;
        String code = (String) entry[0];
        long expire = (Long) entry[1];
        if (System.currentTimeMillis() > expire) return false;
        return code.equalsIgnoreCase(input.trim());
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        return sb.toString();
    }

    private void cleanup() {
        if (store.size() < 512) return;   // 小规模不清理
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> (Long) e.getValue()[1] < now);
    }
}
