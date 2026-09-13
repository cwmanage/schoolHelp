package com.schoolhelp.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人课表作息配置（每节课起止时间，每个用户一套）
 */
@Data
@TableName("schedule_time_config")
public class ScheduleTimeConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 节次 1-12 */
    private Integer section;

    /** 开始时间 HH:mm */
    private String startTime;

    /** 结束时间 HH:mm */
    private String endTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
