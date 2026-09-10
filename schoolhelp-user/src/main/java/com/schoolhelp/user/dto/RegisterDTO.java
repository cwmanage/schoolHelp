package com.schoolhelp.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度需3-20位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度需8-32位")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码需同时包含字母和数字")
    private String password;

    /** 确认密码（前端二次确认，后端也校验一次） */
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

    private String nickname;

    /** 学号(可选) */
    private String schoolId;

    private String college;

    private String className;

    /** 注册角色：0同学 1班长（管理员由管理员创建/数据库） */
    private Integer role;
}
