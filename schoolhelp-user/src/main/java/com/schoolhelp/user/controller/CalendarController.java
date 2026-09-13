package com.schoolhelp.user.controller;

import com.schoolhelp.common.result.Result;
import com.schoolhelp.common.util.UserContext;
import com.schoolhelp.user.entity.CampusCalendarEvent;
import com.schoolhelp.user.service.ScheduleExtraService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 校园日历（考试/竞赛/节假日/活动；登录后可查，更新有提示）
 */
@RestController
@RequestMapping("/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final ScheduleExtraService scheduleExtraService;

    /** 日历事件（?from=2026-09-14&to=2026-09-20，缺省本周） */
    @GetMapping("/events")
    public Result<List<CampusCalendarEvent>> events(@RequestParam(required = false) String from,
                                                    @RequestParam(required = false) String to) {
        return Result.ok(scheduleExtraService.events(from, to));
    }

    /** 是否有未读日历更新（登录后提示用） */
    @GetMapping("/has-update")
    public Result<Boolean> hasUpdate() {
        return Result.ok(scheduleExtraService.hasUpdate(UserContext.getUserId()));
    }

    /** 标记已读 */
    @PostMapping("/mark-read")
    public Result<Void> markRead() {
        scheduleExtraService.markRead(UserContext.getUserId());
        return Result.ok();
    }
}
