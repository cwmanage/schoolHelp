package com.schoolhelp.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.schoolhelp.common.exception.BusinessException;
import com.schoolhelp.user.dto.TimeConfigDTO;
import com.schoolhelp.user.entity.ScheduleTimeConfig;
import com.schoolhelp.user.mapper.ScheduleTimeConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人作息配置服务：每节课起止时间（每用户一套）
 * 未配置时返回默认大学作息模板；保存为全量覆盖。
 */
@Service
@RequiredArgsConstructor
public class ScheduleTimeService {

    private final ScheduleTimeConfigMapper timeConfigMapper;

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    /** 默认大学作息模板（用户未自定义时展示）：上午4节/下午4节/晚上4节，第2-3节、6-7节大课间20分钟 */
    private static final String[][] DEFAULT_TEMPLATE = {
            {"08:00", "08:45"}, {"08:55", "09:40"}, {"10:00", "10:45"}, {"10:55", "11:40"},
            {"14:00", "14:45"}, {"14:55", "15:40"}, {"16:00", "16:45"}, {"16:55", "17:40"},
            {"19:00", "19:45"}, {"19:55", "20:40"}, {"20:50", "21:35"}, {"21:45", "22:30"}
    };

    /** 查询用户作息：无配置时返回默认模板（section=1..12） */
    public List<Map<String, Object>> getTimeConfig(Long userId) {
        List<ScheduleTimeConfig> saved = timeConfigMapper.selectList(
                Wrappers.<ScheduleTimeConfig>lambdaQuery()
                        .eq(ScheduleTimeConfig::getUserId, userId)
                        .orderByAsc(ScheduleTimeConfig::getSection));

        List<Map<String, Object>> out = new ArrayList<>();
        if (saved.isEmpty()) {
            for (int i = 0; i < DEFAULT_TEMPLATE.length; i++) {
                out.add(sectionMap(i + 1, DEFAULT_TEMPLATE[i][0], DEFAULT_TEMPLATE[i][1]));
            }
            return out;
        }
        for (ScheduleTimeConfig c : saved) {
            out.add(sectionMap(c.getSection(), c.getStartTime(), c.getEndTime()));
        }
        return out;
    }

    /** 保存（全量覆盖）：校验节数/时间格式/时序合法 */
    @Transactional
    public void saveTimeConfig(Long userId, TimeConfigDTO dto) {
        List<TimeConfigDTO.SectionTime> sections = dto.getSections();
        // 节次连续、不重复
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).getSection() != i + 1) {
                throw new BusinessException(400, "节次必须从 1 连续编号");
            }
        }
        // 时间合法：start < end，且下一节 start >= 上一节 end
        for (int i = 0; i < sections.size(); i++) {
            TimeConfigDTO.SectionTime s = sections.get(i);
            LocalTime start = LocalTime.parse(s.getStartTime(), HHMM);
            LocalTime end = LocalTime.parse(s.getEndTime(), HHMM);
            if (!start.isBefore(end)) {
                throw new BusinessException(400, "第 " + s.getSection() + " 节开始时间必须早于结束时间");
            }
            if (i > 0) {
                LocalTime prevEnd = LocalTime.parse(sections.get(i - 1).getEndTime(), HHMM);
                if (start.isBefore(prevEnd)) {
                    throw new BusinessException(400, "第 " + s.getSection() + " 节开始时间不能早于上一节结束时间");
                }
            }
        }

        timeConfigMapper.delete(Wrappers.<ScheduleTimeConfig>lambdaQuery()
                .eq(ScheduleTimeConfig::getUserId, userId));
        for (TimeConfigDTO.SectionTime s : sections) {
            ScheduleTimeConfig c = new ScheduleTimeConfig();
            c.setUserId(userId);
            c.setSection(s.getSection());
            c.setStartTime(s.getStartTime());
            c.setEndTime(s.getEndTime());
            timeConfigMapper.insert(c);
        }
    }

    /** AI 时间建议：给节数返回建议作息（解析失败/非法时回退默认模板前 N 节） */
    public List<Map<String, Object>> aiSuggest(Long userId, int sections, String aiReply) {
        List<Map<String, Object>> parsed = parseAiSchedule(aiReply, sections);
        if (parsed != null) {
            return parsed;
        }
        // 回退：默认模板前 N 节
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < sections; i++) {
            out.add(sectionMap(i + 1, DEFAULT_TEMPLATE[i][0], DEFAULT_TEMPLATE[i][1]));
        }
        return out;
    }

    /** 解析 AI 返回的 JSON 数组并校验合法性；失败返回 null */
    private List<Map<String, Object>> parseAiSchedule(String reply, int sections) {
        try {
            String json = extractJsonArray(reply);
            if (json == null) {
                return null;
            }
            List<?> raw = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, List.class);
            if (raw.size() != sections) {
                return null;
            }
            List<Map<String, Object>> out = new ArrayList<>();
            LocalTime prevEnd = null;
            for (int i = 0; i < raw.size(); i++) {
                Object o = raw.get(i);
                if (!(o instanceof Map)) {
                    return null;
                }
                Map<?, ?> m = (Map<?, ?>) o;
                int section = Integer.parseInt(String.valueOf(m.get("section")));
                String start = normalizeTime(String.valueOf(m.get("start")));
                String end = normalizeTime(String.valueOf(m.get("end")));
                if (section != i + 1) {
                    return null;
                }
                LocalTime s = LocalTime.parse(start, HHMM);
                LocalTime e = LocalTime.parse(end, HHMM);
                if (!s.isBefore(e) || (prevEnd != null && s.isBefore(prevEnd))) {
                    return null;
                }
                prevEnd = e;
                out.add(sectionMap(section, start, end));
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeTime(String t) {
        t = t == null ? "" : t.trim();
        // "8:00" → "08:00"
        if (t.matches("^\\d{1,2}:\\d{2}$")) {
            String[] p = t.split(":");
            return String.format("%02d:%s", Integer.parseInt(p[0]), p[1]);
        }
        return t;
    }

    private String extractJsonArray(String text) {
        if (text == null) {
            return null;
        }
        text = text.replace("```json", "```").replace("```", "").trim();
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    private Map<String, Object> sectionMap(int section, String start, String end) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("section", section);
        m.put("startTime", start);
        m.put("endTime", end);
        return m;
    }
}
