package com.schoolhelp.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户
 */
@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    /** 0同学 1班长 2管理员 */
    private Integer role;

    private String schoolId;

    private String college;

    private String className;

    private String signature;

    /** 0系统默认 1自定义上传 */
    private Integer avatarType;

    private String avatarUrl;

    /** 1正常 0禁用 */
    private Integer status;

    /** 班长审批状态：0待审批 1已通过 2已驳回（同学恒为1） */
    private Integer approveStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
