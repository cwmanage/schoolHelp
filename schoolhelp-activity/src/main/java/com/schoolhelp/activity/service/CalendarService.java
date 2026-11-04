package com.schoolhelp.activity.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.schoolhelp.activity.entity.CampusCalendarEvent;
import com.schoolhelp.activity.entity.UserCalendarRead;
import com.schoolhelp.activity.mapper.CampusCalendarEventMapper;
import com.schoolhelp.activity.mapper.UserCalendarReadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 校园日历服务（从 user 服务 ScheduleExtraService 拆出，高内聚）：
 * 事件区间查询 / 未读更新判断 / 标记已读。
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private final CampusCalendarEventMapper calendarEventMapper;
    private final UserCalendarReadMapper calendarReadMapper;

    /** 按日期区间查事件（含覆盖该区间的区间型事件）；缺省本周 */
    public List<CampusCalendarEvent> events(String from, String to) {
        LocalDate f = parseDate(from, LocalDate.now());
        LocalDate t = parseDate(to, f.plusDays(6));
        List<CampusCalendarEvent> all = calendarEventMapper.selectList(
                Wrappers.<CampusCalendarEvent>lambdaQuery()
                        .orderByAsc(CampusCalendarEvent::getEventDate));
        return all.stream().filter(e -> overlaps(e, f, t)).toList();
    }

    /** 用户是否有未读的日历更新 */
    public boolean hasUpdate(Long userId) {
        CampusCalendarEvent latest = calendarEventMapper.selectOne(
                Wrappers.<CampusCalendarEvent>lambdaQuery()
                        .orderByDesc(CampusCalendarEvent::getDataVersion)
                        .last("LIMIT 1"));
        if (latest == null) {
            return false;
        }
        UserCalendarRead read = calendarReadMapper.selectById(userId);
        return read == null || read.getLastReadAt().isBefore(latest.getDataVersion());
    }

    /** 标记用户已读日历 */
    public void markRead(Long userId) {
        UserCalendarRead read = calendarReadMapper.selectById(userId);
        if (read == null) {
            read = new UserCalendarRead();
            read.setUserId(userId);
            read.setLastReadAt(LocalDateTime.now());
            calendarReadMapper.insert(read);
        } else {
            read.setLastReadAt(LocalDateTime.now());
            calendarReadMapper.updateById(read);
        }
    }

    private boolean overlaps(CampusCalendarEvent e, LocalDate from, LocalDate to) {
        LocalDate s = e.getEventDate() != null ? e.getEventDate() : e.getDateStart();
        LocalDate end = e.getEventDate() != null ? e.getEventDate() : (e.getDateEnd() != null ? e.getDateEnd() : e.getDateStart());
        if (s == null) {
            return false;
        }
        return !s.isAfter(to) && (end == null || !end.isBefore(from));
    }

    private LocalDate parseDate(String s, LocalDate def) {
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return def;
        }
    }
}
