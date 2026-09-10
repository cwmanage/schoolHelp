package com.schoolhelp.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业
 */
@Data
@TableName("assignment")
public class Assignment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private String title;

    private String content;

    /** 截止提交时间 */
    private LocalDateTime deadline;

    private Long creatorId;

    /** 申请人ID */
    private Long applyUserId;

    /** 申请说明 */
    private String applyNote;

    /** 审批意见 */
    private String reviewNote;

    /** 审批时间 */
    private LocalDateTime reviewedAt;

    /** 0待审批 1已通过 2已驳回 */
    private Integer status;

    private LocalDateTime createdAt;
}
