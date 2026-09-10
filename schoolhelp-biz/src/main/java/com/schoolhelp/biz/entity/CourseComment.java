package com.schoolhelp.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程评论（匿名存真实 user_id，供管理员查删）
 */
@Data
@TableName("course_comment")
public class CourseComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private Long userId;

    private String content;

    /** 0实名 1匿名 */
    private Integer isAnonymous;

    /** 1正常 0已删除 */
    private Integer status;

    private LocalDateTime createdAt;
}
