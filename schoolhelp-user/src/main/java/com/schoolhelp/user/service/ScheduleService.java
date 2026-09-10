package com.schoolhelp.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.schoolhelp.common.exception.BusinessException;
import com.schoolhelp.user.dto.ScheduleDTO;
import com.schoolhelp.user.entity.Schedule;
import com.schoolhelp.user.mapper.ScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 个人课表服务
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleMapper scheduleMapper;

    /** 我的课表（按星期排，前端按支付宝样式渲染） */
    public List<Schedule> mySchedules(Long userId, String semester) {
        return scheduleMapper.selectList(Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getUserId, userId)
                .eq(semester != null && !semester.isEmpty(), Schedule::getSemester, semester)
                .orderByAsc(Schedule::getWeekDay, Schedule::getStartSection));
    }

    /** 我有哪些学期（课表切换用） */
    public List<String> mySemesters(Long userId) {
        return scheduleMapper.selectList(Wrappers.<Schedule>lambdaQuery()
                        .eq(Schedule::getUserId, userId)
                        .select(Schedule::getSemester))
                .stream().map(Schedule::getSemester).distinct().toList();
    }

    /** 新增（可关联课程库 courseId，也可纯自录） */
    public Long add(Long userId, ScheduleDTO dto) {
        if (dto.getEndSection() < dto.getStartSection()) {
            throw new BusinessException(400, "结束节次不能早于开始节次");
        }
        Schedule s = new Schedule();
        s.setUserId(userId);
        s.setCourseId(dto.getCourseId());
        s.setCourseName(dto.getCourseName());
        s.setWeekDay(dto.getWeekDay());
        s.setStartSection(dto.getStartSection());
        s.setEndSection(dto.getEndSection());
        s.setWeekType(dto.getWeekType() == null ? 0 : dto.getWeekType());
        s.setWeeks(dto.getWeeks());
        s.setRoom(dto.getRoom());
        s.setTeacher(dto.getTeacher());
        s.setSemester(dto.getSemester());
        scheduleMapper.insert(s);
        return s.getId();
    }

    /** 修改（仅本人） */
    public void update(Long userId, Long id, ScheduleDTO dto) {
        Schedule exist = scheduleMapper.selectById(id);
        if (exist == null || !exist.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该课表条目");
        }
        if (dto.getEndSection() < dto.getStartSection()) {
            throw new BusinessException(400, "结束节次不能早于开始节次");
        }
        exist.setCourseId(dto.getCourseId());
        exist.setCourseName(dto.getCourseName());
        exist.setWeekDay(dto.getWeekDay());
        exist.setStartSection(dto.getStartSection());
        exist.setEndSection(dto.getEndSection());
        exist.setWeekType(dto.getWeekType() == null ? 0 : dto.getWeekType());
        exist.setWeeks(dto.getWeeks());
        exist.setRoom(dto.getRoom());
        exist.setTeacher(dto.getTeacher());
        exist.setSemester(dto.getSemester());
        scheduleMapper.updateById(exist);
    }

    /** 删除（仅本人） */
    public void delete(Long userId, Long id) {
        Schedule exist = scheduleMapper.selectById(id);
        if (exist == null || !exist.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该课表条目");
        }
        scheduleMapper.deleteById(id);
    }
}
