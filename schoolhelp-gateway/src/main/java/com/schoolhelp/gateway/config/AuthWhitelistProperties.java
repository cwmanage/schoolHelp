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

    /** 是否启用滑动续期：token 剩余有效期不足阈值时自动重签新 token */
    private boolean renewEnabled = true;

    /** 续期阈值（天）：剩余有效期低于该值时重签新 token（有效期仍为 JwtUtil 固定的 7 天） */
    private int renewThresholdDays = 3;
}
