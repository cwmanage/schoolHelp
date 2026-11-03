package com.schoolhelp.user.agent.tools;

import com.schoolhelp.user.entity.Schedule;
import com.schoolhelp.user.agent.AgentTool;
import com.schoolhelp.user.agent.ToolParam;
import com.schoolhelp.user.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课表工具：把 ScheduleService 的能力暴露给 Agent。
 * 注意返回字段是刻意裁剪的——实体全量字段会浪费 token 且干扰模型作答。
 */
@Component
@RequiredArgsConstructor
public class ScheduleTools {

    private final ScheduleService scheduleService;

    @AgentTool(name = "get_my_schedule",
            description = "查询当前登录用户的课程表。当用户询问课程、课表、上课安排、某天有没有课时使用。"
                    + "semester 不确定时留空，默认返回全部学期。")
    public List<Map<String, Object>> getMySchedule(
            @ToolParam(name = "semester", description = "学期，如 2026-2027-1；不确定可传空字符串", required = false)
            String semester) {
        List<Schedule> list = scheduleService.mySchedules(UserIdHolder.get(),
                semester == null ? "" : semester);
        return list.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseName", s.getCourseName());
            m.put("weekDay", s.getWeekDay());          // 1=周一…7=周日
            m.put("startSection", s.getStartSection());
            m.put("endSection", s.getEndSection());
            m.put("weekType", s.getWeekType());        // 0每周 1单周 2双周
            m.put("weeks", s.getWeeks());
            m.put("room", s.getRoom());
            m.put("teacher", s.getTeacher());
            m.put("semester", s.getSemester());
            return m;
        }).toList();
    }

    /** 工具方法在请求线程内执行，直接从 ThreadLocal 取当前用户 */
    private static final class UserIdHolder {
        static Long get() {
            return com.schoolhelp.common.util.UserContext.getUserId();
        }
    }
}
