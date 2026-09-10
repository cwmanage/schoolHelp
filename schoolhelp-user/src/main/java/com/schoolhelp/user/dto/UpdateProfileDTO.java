package com.schoolhelp.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新个人资料请求
 */
@Data
public class UpdateProfileDTO {

    private String nickname;

    /** 学号(可选绑定) */
    private String schoolId;

    private String college;

    private String className;

    @Size(max = 200, message = "签名最多200字")
    private String signature;
}
