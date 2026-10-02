package com.schoolhelp.common.annotation;

import java.lang.annotation.*;

/**
 * 通用资源权限注解，用于接口功能校验，防御垂直越权
 * 标记controller方法，访问接口需要拥有对应资源编码权限
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ResourcePermission {
    /**
     * 资源唯一编码，如：sale_report、finance_export、device_query
     */
    String resourceCode();
}
