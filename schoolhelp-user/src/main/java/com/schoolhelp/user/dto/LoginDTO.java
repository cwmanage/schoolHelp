package com.schoolhelp.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求
 */
@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码凭据：本地/冒烟豁免模式（SCHOOLHELP_CAPTCHA_DISABLED）下可为空，生产强制（CaptchaService 校验） */
    private String captchaId;

    private String captchaCode;
}
