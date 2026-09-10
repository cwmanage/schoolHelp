package com.schoolhelp.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业提交标记（用户点"已提交"后不再滚动提示）
 */
@Data
@TableName("assignment_submit")
public class AssignmentSubmit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assignmentId;

    private Long userId;

    private LocalDateTime submitTime;
}
