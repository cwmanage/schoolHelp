package com.schoolhelp.biz.service;

import com.schoolhelp.biz.entity.AssignmentSubmit;
import com.schoolhelp.biz.feign.CourseFeignClient;
import com.schoolhelp.biz.feign.UserFeignClient;
import com.schoolhelp.biz.mapper.AssignmentSubmitMapper;
import com.schoolhelp.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 临期作业聚合服务：
 * 我的课表(courseId) ∩ 作业deadline ≤ 5天 ∩ 未提交 → 红色条幅滚动提示
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrgentAssignmentService {

    private final UserFeignClient userFeignClient;
    private final CourseFeignClient courseFeignClient;
    private final AssignmentSubmitMapper submitMapper;

    /** 距离提醒的天数阈值 */
    private static final int URGENT_DAYS = 5;

    public List<Map<String, Object>> urgentAssignments(Long userId, String semester) {
        // 1. 我的课表
        Result<List<Map<String, Object>>> scheduleResult = userFeignClient.mySchedules(userId, semester);
        if (scheduleResult == null || !scheduleResult.isSuccess() || scheduleResult.getData() == null) {
            log.warn("获取课表失败: {}", scheduleResult);
            return List.of();
        }
        // 课表中的课程ID（含自录无 courseId 的，跳过）
        Set<Long> courseIds = scheduleResult.getData().stream()
                .map(s -> toLong(s.get("courseId")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (courseIds.isEmpty()) {
            return List.of();
        }

        // 2. 逐个课程拉作业（课程数少，串行可接受；量大可改批量接口）
        List<Map<String, Object>> urgent = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadlineBoundary = now.plusDays(URGENT_DAYS);

        for (Long courseId : courseIds) {
            try {
                Result<List<Map<String, Object>>> asnResult =
                        courseFeignClient.assignmentsByCourse(courseId);
                if (asnResult == null || !asnResult.isSuccess() || asnResult.getData() == null) {
                    continue;
                }
                for (Map<String, Object> a : asnResult.getData()) {
                    // 作业状态过滤
                    Object statusObj = a.get("status");
                    if (statusObj != null && !"1".equals(String.valueOf(statusObj))) {
                        continue;
                    }
                    LocalDateTime deadline = toLocalDateTime(a.get("deadline"));
                    if (deadline == null) {
                        continue;
                    }
                    // 已过期不算（deadline < now 忽略，可扩展为"已逾期未交"也提示）
                    if (deadline.isBefore(now)) {
                        continue;
                    }
                    if (deadline.isAfter(deadlineBoundary)) {
                        continue; // 超过5天不提示
                    }
                    Long assignmentId = toLong(a.get("id"));
                    if (assignmentId == null) {
                        continue;
                    }
                    // 未提交才提示
                    boolean submitted = isSubmitted(assignmentId, userId);
                    if (submitted) {
                        continue;
                    }
                    Map<String, Object> item = new HashMap<>();
                    item.put("assignmentId", assignmentId);
                    item.put("courseId", courseId);
                    item.put("courseName", a.get("courseName") != null ? a.get("courseName") : "课程");
                    item.put("title", a.get("title"));
                    item.put("content", a.get("content"));
                    item.put("deadline", a.get("deadline"));
                    item.put("daysLeft", daysBetween(now, deadline));
                    urgent.add(item);
                }
            } catch (Exception e) {
                log.warn("拉取课程{}作业失败: {}", courseId, e.getMessage());
            }
        }

        // 3. 按截止时间升序（最急的排最前）
        urgent.sort(Comparator.comparing(m -> String.valueOf(m.get("deadline"))));
        return urgent;
    }

    private boolean isSubmitted(Long assignmentId, Long userId) {
        Long count = submitMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<AssignmentSubmit>lambdaQuery()
                        .eq(AssignmentSubmit::getAssignmentId, assignmentId)
                        .eq(AssignmentSubmit::getUserId, userId));
        return count != null && count > 0;
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        try {
            if (o instanceof Number n) return n.longValue();
            String s = String.valueOf(o).trim();
            return s.isEmpty() || "null".equals(s) ? null : Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime toLocalDateTime(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDateTime ldt) return ldt;
        try {
            return LocalDateTime.parse(String.valueOf(o).replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }

    private long daysBetween(LocalDateTime now, LocalDateTime deadline) {
        return java.time.Duration.between(now, deadline).toDays()
                + (java.time.Duration.between(now, deadline).toHoursPart() > 0 ? 1 : 0);
    }
}
