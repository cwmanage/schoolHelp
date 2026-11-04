package com.schoolhelp.activity.controller;

import com.schoolhelp.activity.entity.CampusCalendarEvent;
import com.schoolhelp.activity.service.CalendarService;
import com.schoolhelp.common.result.Result;
import com.schoolhelp.common.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 校园日历（考试/竞赛/节假日/活动；登录后可查，更新有提示）
 * 网关路由：/api/activity/** → 本服务（StripPrefix=2 → /calendar/**）
 */
@RestController
@RequestMapping("/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    /** 日历事件（?from=2026-09-14&to=2026-09-20，缺省本周） */
    @GetMapping("/events")
    public Result<List<CampusCalendarEvent>> events(@RequestParam(required = false) String from,
                                                    @RequestParam(required = false) String to) {
        return Result.ok(calendarService.events(from, to));
    }

    /** 是否有未读日历更新（登录后提示用） */
    @GetMapping("/has-update")
    public Result<Boolean> hasUpdate() {
        return Result.ok(calendarService.hasUpdate(UserContext.getUserId()));
    }

    /** 标记已读 */
    @PostMapping("/mark-read")
    public Result<Void> markRead() {
        calendarService.markRead(UserContext.getUserId());
        return Result.ok();
    }
}
