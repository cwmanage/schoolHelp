package com.schoolhelp.gateway.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 报文加解密（前后端传输加密）。
 * 密文格式："{iv 的 Base64}.{密文的 Base64}"；密钥为 64 位 hex（32 字节）。
 * 边界说明：前端密钥会被打包进 bundle，本加密防的是「F12/抓包直接看到明文」，
 * 不防专业逆向——真正的安全底线是 HTTPS + 服务端校验。
 */
public final class CryptoUtil {

    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec keySpec;

    private CryptoUtil(SecretKeySpec keySpec) {
        this.keySpec = keySpec;
    }

    /** 由 64 位 hex 密钥构建；格式非法返回 null（调用方禁用加密） */
    public static CryptoUtil fromHexKey(String hexKey) {
        if (hexKey == null || hexKey.length() != 64 || !hexKey.matches("[0-9a-fA-F]+")) {
            return null;
        }
        try {
            byte[] key = hexToBytes(hexKey);
            return new CryptoUtil(new SecretKeySpec(key, "AES"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 加密 JSON 明文 → "iv.cipher" 密文串 */
    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LEN];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + "."
                    + Base64.getEncoder().encodeToString(enc);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /** 解密 "iv.cipher" 密文串 → JSON 明文；格式/密钥不符返回 null（调用方按非法请求拒绝） */
    public String decrypt(String payload) {
        if (payload == null || payload.isEmpty()) return null;
        int dot = payload.indexOf('.');
        if (dot <= 0 || dot == payload.length() - 1) return null;
        try {
            byte[] iv = Base64.getDecoder().decode(payload.substring(0, dot));
            byte[] enc = Base64.getDecoder().decode(payload.substring(dot + 1));
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(enc), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** 判定 JSON 体是否为加密报文（{"payload":"iv.cipher"} 结构） */
    public static boolean isEncryptedBody(String json) {
        if (json == null) return false;
        String s = json.strip();
        return s.startsWith("{\"payload\":\"") && s.endsWith("\"}") && s.contains(".");
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }
}
