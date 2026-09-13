package com.schoolhelp.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学期设置（第一周开始日期，每用户每学期一份）
 */
@Data
@TableName("schedule_semester_setting")
public class SemesterSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String semester;

    /** 第一周周一日期 */
    private LocalDate week1Date;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
