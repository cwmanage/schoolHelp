package com.schoolhelp.common.security.aspect;


import com.schoolhelp.common.annotation.ResourcePermission;
import com.schoolhelp.common.exception.BusinessException;
import com.schoolhelp.common.security.checker.ResourcePermissionChecker;
import com.schoolhelp.common.util.UserContext;
import jakarta.annotation.Resource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

@Aspect
public class ResourcePermissionAspect {
    @Resource
    private ResourcePermissionChecker resourcePermissionChecker;

    @Pointcut("@annotation(com.schoolhelp.common.annotation.ResourcePermission)")
    public void resourcePermissionPointCut(){}

    @Around("resourcePermissionPointCut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "用户未登录");
        }
        //通过切入点（joinPoint）拿到注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        ResourcePermission annotation = method.getAnnotation(ResourcePermission.class);
        String resourceCode = annotation.resourceCode();

        boolean pass = resourcePermissionChecker.hasPermission(userId, resourceCode);
        if (!pass) {
            throw new BusinessException(403, "无访问资源[" + resourceCode + "]的权限");
        }
        return joinPoint.proceed();
    }
}
