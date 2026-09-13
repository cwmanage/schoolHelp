package com.schoolhelp.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 调课记录（临时调课=仅生效周有效）
 */
@Data
@TableName("schedule_change")
public class ScheduleChange {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 原课表条目 */
    private Long scheduleId;

    /** 生效周（周一起始，临时调课=本周） */
    private LocalDate weekStart;

    /** 调至星期 1-7 */
    private Integer weekDay;

    private Integer startSection;

    private Integer endSection;

    private LocalDateTime createdAt;
}
