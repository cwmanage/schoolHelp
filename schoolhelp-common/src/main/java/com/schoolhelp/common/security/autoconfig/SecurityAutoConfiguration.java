package com.schoolhelp.common.security.autoconfig;

import com.schoolhelp.common.security.aspect.ResourcePermissionAspect;
import com.schoolhelp.common.security.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SecurityAutoConfiguration implements WebMvcConfigurer {
    @Bean
    public ResourcePermissionAspect resourcePermissionAspect() {
        return new ResourcePermissionAspect();
    }

    @Bean
    public LoginInterceptor loginInterceptor() {
        return new LoginInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor()).addPathPatterns("/**");

    }
}
