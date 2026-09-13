package com.schoolhelp.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 校园日历事件（考试/竞赛/节假日/活动；由每日同步任务维护）
 */
@Data
@TableName("campus_calendar_event")
public class CampusCalendarEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** exam考试 / contest竞赛 / holiday节假日 / activity活动 */
    private String eventType;

    private String title;

    /** 单日事件日期 */
    private LocalDate eventDate;

    /** 区间开始 */
    private LocalDate dateStart;

    /** 区间结束 */
    private LocalDate dateEnd;

    /** 模糊时间说明（如 上午 / 全天 / 以官方通知为准） */
    private String timeNote;

    private String detail;

    private String sourceUrl;

    /** 内容版本（仅内容变化时更新，用于"有更新"提示） */
    private LocalDateTime dataVersion;

    private LocalDateTime createdAt;
}
