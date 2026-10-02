package com.schoolhelp.common.security.checker;

/**
 * 权限校验抽象接口，由业务微服务实现
 */
public interface ResourcePermissionChecker {
    /**
     * 校验用户是否拥有指定资源权限
     * @param userId 用户ID
     * @param resourceCode 资源编码
     * @return true=有权限 false=无权限
     */
    boolean hasPermission(Long userId, String resourceCode);
}
