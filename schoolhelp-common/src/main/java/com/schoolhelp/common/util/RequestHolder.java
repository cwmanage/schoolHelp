package com.schoolhelp.common.util;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求上下文获取
 */
public class RequestHolder {

    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    public static String getHeader(String name) {
        HttpServletRequest req = getRequest();
        return req == null ? null : req.getHeader(name);
    }
}
