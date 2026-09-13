package com.schoolhelp.user.controller;

import com.schoolhelp.common.result.Result;
import com.schoolhelp.common.util.UserContext;
import com.schoolhelp.user.dto.ScheduleDTO;
import com.schoolhelp.user.dto.TimeConfigDTO;
import com.schoolhelp.user.entity.Schedule;
import com.schoolhelp.user.service.ScheduleExtraService;
import com.schoolhelp.user.service.ScheduleService;
import com.schoolhelp.user.service.ScheduleTimeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 个人课表接口（支付宝课表样式数据源）
 */
@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ScheduleTimeService scheduleTimeService;
    private final ScheduleExtraService scheduleExtraService;

    /** 我的作息配置（每节课起止；未配置时返回默认模板） */
    @GetMapping("/time-config")
    public Result<List<Map<String, Object>>> timeConfig() {
        return Result.ok(scheduleTimeService.getTimeConfig(UserContext.getUserId()));
    }

    /** 保存作息配置（全量覆盖） */
    @PutMapping("/time-config")
    public Result<Void> saveTimeConfig(@Valid @RequestBody TimeConfigDTO dto) {
        scheduleTimeService.saveTimeConfig(UserContext.getUserId(), dto);
        return Result.ok();
    }

    /** 学期设置：第一周开始日期（未设置 week1Date 为 null） */
    @GetMapping("/semester-setting")
    public Result<Map<String, Object>> semesterSetting(@RequestParam String semester) {
        return Result.ok(scheduleExtraService.getSemesterSetting(UserContext.getUserId(), semester));
    }

    /** 保存学期第一周开始日期 */
    @PutMapping("/semester-setting")
    public Result<Void> saveSemesterSetting(@RequestBody Map<String, String> body) {
        scheduleExtraService.saveSemesterSetting(
                UserContext.getUserId(),
                body.get("semester"),
                body.get("week1Date") == null ? null : java.time.LocalDate.parse(body.get("week1Date")));
        return Result.ok();
    }

    /** 本周调课记录（weekStart=周一日期） */
    @GetMapping("/changes")
    public Result<List<com.schoolhelp.user.entity.ScheduleChange>> changes(@RequestParam String weekStart) {
        return Result.ok(scheduleExtraService.changes(UserContext.getUserId(), java.time.LocalDate.parse(weekStart)));
    }

    /** 新增调课（临时调课=本周生效） */
    @PostMapping("/change")
    public Result<Long> addChange(@RequestBody Map<String, Object> body) {
        Long scheduleId = Long.valueOf(String.valueOf(body.get("scheduleId")));
        int weekDay = Integer.parseInt(String.valueOf(body.get("weekDay")));
        int startSection = Integer.parseInt(String.valueOf(body.get("startSection")));
        int endSection = Integer.parseInt(String.valueOf(body.get("endSection")));
        return Result.ok(scheduleExtraService.addChange(UserContext.getUserId(), scheduleId, weekDay, startSection, endSection));
    }

    /** 恢复原时间（删除调课记录） */
    @DeleteMapping("/change/{id}")
    public Result<Void> deleteChange(@PathVariable Long id) {
        scheduleExtraService.deleteChange(UserContext.getUserId(), id);
        return Result.ok();
    }

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
