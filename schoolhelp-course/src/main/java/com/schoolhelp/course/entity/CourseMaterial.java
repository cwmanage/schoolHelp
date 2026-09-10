package com.schoolhelp.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程资料
 */
@Data
@TableName("course_material")
public class CourseMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private String title;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private String fileType;

    private Long uploaderId;

    /** 0待审批 1已通过 2已驳回 */
    private Integer status;

    /** 申请人ID */
    private Long applyUserId;

    /** 申请说明 */
    private String applyNote;

    /** 审批意见 */
    private String reviewNote;

    /** 审批时间 */
    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;
}
