package com.schoolhelp.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人课表条目（学生自录，支付宝课表样式展示）
 */
@Data
@TableName("schedule")
public class Schedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 关联课程库ID（可空=纯自录） */
    private Long courseId;

    private String courseName;

    /** 星期 1-7 */
    private Integer weekDay;

    /** 开始节次 */
    private Integer startSection;

    /** 结束节次 */
    private Integer endSection;

    /** 0每周 1单周 2双周 */
    private Integer weekType;

    /** 具体周次，如1-16 / 1,3,5 */
    private String weeks;

    private String room;

    private String teacher;

    /** 学期，如 2026-2027-1 */
    private String semester;

    private LocalDateTime createdAt;
}
