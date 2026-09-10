package com.schoolhelp.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具（HS256）
 */
public class JwtUtil {

    private static final String SECRET = "schoolHelp-S3cret-Key-2026-For-JWT-Sign-HS256!!";

    private static SecretKey key() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 token，claims: userId, username, role
     */
    public static String createToken(Long userId, String username, Integer role) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + 7 * 24 * 3600 * 1000L); // 7天
        return Jwts.builder()
                .claim("userId", userId)
                .claim("username", username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expire)
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 token，失败/过期返回 null
     */
    public static Claims parse(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(key()).build()
                    .parseClaimsJws(token).getBody();
        } catch (Exception e) {
            return null;
        }
    }
}
