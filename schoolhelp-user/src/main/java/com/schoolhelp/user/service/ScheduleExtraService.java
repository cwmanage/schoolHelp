package com.schoolhelp.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.schoolhelp.common.exception.BusinessException;
import com.schoolhelp.user.entity.SemesterSetting;
import com.schoolhelp.user.mapper.ScheduleChangeMapper;
import com.schoolhelp.user.mapper.SemesterSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学期设置 + 调课服务（校园日历已拆至 schoolhelp-activity 服务）
 */
@Service
@RequiredArgsConstructor
public class ScheduleExtraService {

    private final SemesterSettingMapper semesterSettingMapper;
    private final ScheduleChangeMapper scheduleChangeMapper;

    // ==================== 学期设置 ====================

    /** 查询学期第一周开始日期（未设置返回 null） */
    public Map<String, Object> getSemesterSetting(Long userId, String semester) {
        SemesterSetting s = semesterSettingMapper.selectOne(
                Wrappers.<SemesterSetting>lambdaQuery()
                        .eq(SemesterSetting::getUserId, userId)
                        .eq(SemesterSetting::getSemester, semester));
        Map<String, Object> out = new HashMap<>();
        out.put("semester", semester);
        out.put("week1Date", s == null ? null : s.getWeek1Date().toString());
        return out;
    }

    /** 保存第一周开始日期（取当周周一） */
    public void saveSemesterSetting(Long userId, String semester, LocalDate week1Date) {
        if (semester == null || semester.isBlank()) {
            throw new BusinessException(400, "学期不能为空");
        }
        if (week1Date == null) {
            throw new BusinessException(400, "请选择第一周开始日期");
        }
        LocalDate monday = week1Date.minusDays(week1Date.getDayOfWeek().getValue() - 1);
        SemesterSetting exist = semesterSettingMapper.selectOne(
                Wrappers.<SemesterSetting>lambdaQuery()
                        .eq(SemesterSetting::getUserId, userId)
                        .eq(SemesterSetting::getSemester, semester));
        if (exist == null) {
            exist = new SemesterSetting();
            exist.setUserId(userId);
            exist.setSemester(semester);
        }
        exist.setWeek1Date(monday);
        if (exist.getId() == null) {
            semesterSettingMapper.insert(exist);
        } else {
            semesterSettingMapper.updateById(exist);
        }
    }

    // ==================== 调课 ====================

    /** 本周调课记录 */
    public List<com.schoolhelp.user.entity.ScheduleChange> changes(Long userId, LocalDate weekStart) {
        LocalDate monday = weekStart.minusDays(weekStart.getDayOfWeek().getValue() - 1);
        return scheduleChangeMapper.selectList(
                Wrappers.<com.schoolhelp.user.entity.ScheduleChange>lambdaQuery()
                        .eq(com.schoolhelp.user.entity.ScheduleChange::getUserId, userId)
                        .eq(com.schoolhelp.user.entity.ScheduleChange::getWeekStart, monday));
    }

    /** 新增临时调课（本周生效；调课前节次占用检查） */
    public Long addChange(Long userId, Long scheduleId, int weekDay, int startSection, int endSection) {
        if (weekDay < 1 || weekDay > 7 || startSection < 1 || endSection > 12 || endSection < startSection) {
            throw new BusinessException(400, "调课时间不合法");
        }
        com.schoolhelp.user.entity.ScheduleChange c = new com.schoolhelp.user.entity.ScheduleChange();
        c.setUserId(userId);
        c.setScheduleId(scheduleId);
        LocalDate today = LocalDate.now();
        c.setWeekStart(today.minusDays(today.getDayOfWeek().getValue() - 1));
        c.setWeekDay(weekDay);
        c.setStartSection(startSection);
        c.setEndSection(endSection);
        scheduleChangeMapper.insert(c);
        return c.getId();
    }

    /** 恢复原时间（仅本人可删自己的调课记录） */
    public void deleteChange(Long userId, Long id) {
        com.schoolhelp.user.entity.ScheduleChange c = scheduleChangeMapper.selectById(id);
        if (c == null || !c.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该调课记录");
        }
        scheduleChangeMapper.deleteById(id);
    }


}
