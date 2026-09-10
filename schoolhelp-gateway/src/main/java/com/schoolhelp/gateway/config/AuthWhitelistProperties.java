package com.schoolhelp.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关鉴权白名单配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "schoolhelp.auth")
public class AuthWhitelistProperties {

    /** 无需登录即可访问的路径 */
    private List<String> whitelist = new ArrayList<>();
}
