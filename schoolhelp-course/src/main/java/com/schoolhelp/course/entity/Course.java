package com.schoolhelp.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程库（班长/管理员维护）
 */
@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String teacherName;

    /** 学校师资个人主页链接 */
    private String teacherLink;

    private String className;

    private String semester;

    private String description;

    private Long creatorId;

    /** 申请人ID（全员可申请，管理员/班长直建=自己） */
    private Long applyUserId;

    /** 申请说明 */
    private String applyNote;

    /** 审批意见（驳回原因） */
    private String reviewNote;

    /** 审批时间 */
    private LocalDateTime reviewedAt;

    /** 0待审批 1已通过 2已驳回 3下架 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
