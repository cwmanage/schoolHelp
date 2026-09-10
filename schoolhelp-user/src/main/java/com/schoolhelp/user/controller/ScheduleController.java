package com.schoolhelp.user.controller;

import com.schoolhelp.common.result.Result;
import com.schoolhelp.common.util.UserContext;
import com.schoolhelp.user.dto.ScheduleDTO;
import com.schoolhelp.user.entity.Schedule;
import com.schoolhelp.user.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 个人课表接口（支付宝课表样式数据源）
 */
@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /** 我的课表（?semester=2026-2027-1 可切换学期） */
    @GetMapping
    public Result<List<Schedule>> mySchedules(@RequestParam(required = false) String semester) {
        return Result.ok(scheduleService.mySchedules(UserContext.getUserId(), semester));
    }

    /** 我的学期列表（切换下拉） */
    @GetMapping("/semesters")
    public Result<List<String>> mySemesters() {
        return Result.ok(scheduleService.mySemesters(UserContext.getUserId()));
    }

    /** 内部接口（biz-service Feign 调用）：按指定用户查课表 */
    @GetMapping("/list-by-user")
    public Result<List<Schedule>> listByUser(@RequestParam Long userId,
                                             @RequestParam(required = false) String semester) {
        return Result.ok(scheduleService.mySchedules(userId, semester));
    }

    /** 新增课表条目（关联课程可选） */
    @PostMapping
    public Result<Long> add(@Valid @RequestBody ScheduleDTO dto) {
        return Result.ok(scheduleService.add(UserContext.getUserId(), dto));
    }

    /** 修改（本人） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ScheduleDTO dto) {
        scheduleService.update(UserContext.getUserId(), id, dto);
        return Result.ok();
    }

    /** 删除（本人） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scheduleService.delete(UserContext.getUserId(), id);
        return Result.ok();
    }
}
