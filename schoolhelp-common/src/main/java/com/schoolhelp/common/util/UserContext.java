package com.schoolhelp.common.util;

/**
 * 当前登录用户上下文（Gateway 解析 JWT 后写入请求头，各服务读取）
 */
public class UserContext {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_ROLE = "X-Role";

    public static Long getUserId() {
        String v = RequestHolder.getHeader(HEADER_USER_ID);
        return v == null ? null : Long.valueOf(v);
    }

    public static String getUsername() {
        return RequestHolder.getHeader(HEADER_USERNAME);
    }

    public static Integer getRole() {
        String v = RequestHolder.getHeader(HEADER_ROLE);
        return v == null ? null : Integer.valueOf(v);
    }
}
