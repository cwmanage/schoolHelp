package com.schoolhelp.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolhelp.user.config.AiProperties;
import com.schoolhelp.user.entity.CampusCalendarEvent;
import com.schoolhelp.user.mapper.CampusCalendarEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 校园日历数据同步任务：
 * 1. 节假日：每日调 timor.tech 免费接口同步当年国务院公布的节假日/调休
 * 2. 考试/竞赛：每日调智谱大模型生成当年考试竞赛日历（AI 生成失败时回退内置知识库）；
 *    所有事件仅内容变化时更新 data_version（避免重复打扰用户）
 * 启动时执行一次，之后每天 03:10 执行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampusCalendarSyncJob {

    private final CampusCalendarEventMapper eventMapper;
    private final AiProperties aiProperties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper om = new ObjectMapper();

    private static final String HOLIDAY_API = "https://timor.tech/api/holiday/year/";

    /** 内置考试/竞赛兜底知识库（仅 AI 生成失败时使用；日期为典型/预估，均标注以官方通知为准） */
    private static final String[][] EXAM_CONTEST_SEED = {
            // type, title, date, time_note, detail
            {"exam", "全国大学英语四、六级考试（CET 笔试）", "2026-12-19", "预计 12 月中旬，以官方通知为准", "每年 6 月/12 月各一次；报名请关注学校教务通知"},
            {"exam", "全国硕士研究生招生考试（考研初试）", "2026-12-26", "预计 12 月下旬周末，以官方通知为准", "初试通常在 12 月倒数第二个周末"},
            {"exam", "全国计算机等级考试（NCRE）", "2027-03-27", "预计 3 月下旬，以官方通知为准", "每年 3 月/9 月各一次，部分省份 12 月增考"},
            {"exam", "中小学教师资格考试（笔试）", "2027-03-13", "预计 3 月中旬，以官方通知为准", "上下半年各一次笔试，面试另安排"},
            {"contest", "全国大学生数学建模竞赛", "2027-09-09", "预计 9 月第二个周四起，以官方通知为准", "三人组队，72 小时连续作答"},
            {"contest", "蓝桥杯全国软件和信息技术专业人才大赛（省赛）", "2027-04-11", "预计 4 月中旬，以官方通知为准", "每年 4 月省赛、6 月全国总决赛"},
            {"contest", "中国国际大学生创新大赛（原互联网+）省赛", "2027-06-01", "5-8 月分省赛段，以官方通知为准", "校赛-省赛-国赛逐级晋级"},
            {"activity", "全国大学英语四、六级考试（CET 口试）", "2026-11-21", "预计 11 月下旬，以官方通知为准", "笔试前的一个周末举行口试"}
    };

    /** 同步锁（启动与定时并发保护） */
    private final ConcurrentHashMap<String, Boolean> running = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        syncAsync();
    }

    /** 每天 03:10 执行 */
    @Scheduled(cron = "0 10 3 * * ?")
    public void daily() {
        syncAsync();
    }

    private void syncAsync() {
        if (running.putIfAbsent("sync", true) != null) {
            return;
        }
        try {
            syncHolidays();
            if (!aiSyncExamContests()) {
                seedExamContests(); // AI 失败 → 内置知识库兜底
            }
            log.info("校园日历同步完成");
        } catch (Exception e) {
            log.warn("校园日历同步失败（不影响其他功能，下次重试）: {}", e.getMessage());
        } finally {
            running.remove("sync");
        }
    }

    /** AI 生成当年考试/竞赛日历；成功返回 true，失败/解析失败返回 false */
    private boolean aiSyncExamContests() {
        if (!aiProperties.isConfigured()) {
            return false;
        }
        int year = LocalDate.now().getYear();
        String prompt = "你是高校教务日历助手。请列出 " + year + " 年和 " + (year + 1) + " 年中国大学生最常参加的考试与竞赛日历"
                + "（如四六级、考研初试、计算机等级考试、教师资格证、数学建模国赛、蓝桥杯、互联网+大赛等，共 8-14 条）。\n"
                + "规则：\n"
                + "1. date 只填官方已公布或高度确定的惯例日期；无法确定精确日期的，date 填最可能的典型日期，并在 timeNote 里写「预计 X 月，以官方通知为准」\n"
                + "2. type 只能是 exam 或 contest；detail 一句话说明\n"
                + "3. 只输出 JSON 数组本体：[{\"type\":\"exam\",\"title\":\"...\",\"date\":\"2026-12-19\",\"timeNote\":\"...\",\"detail\":\"...\"}]，禁止解释文字、禁止 markdown 标记";
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", aiProperties.getModel());
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("stream", false);
            body.put("max_tokens", 2048);
            body.put("temperature", 0.2);
            Map<String, Object> resp = restClient.post()
                    .uri(aiProperties.getBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + aiProperties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (resp == null) {
                return false;
            }
            List<?> choices = (List<?>) resp.get("choices");
            if (choices == null || choices.isEmpty()) {
                return false;
            }
            Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
            String reply = message == null ? null : String.valueOf(message.get("content"));
            if (reply == null || reply.isBlank()) {
                return false;
            }
            String json = extractJsonArray(reply);
            if (json == null) {
                return false;
            }
            List<?> arr = om.readValue(json, List.class);
            int count = 0;
            for (Object o : arr) {
                if (!(o instanceof Map)) continue;
                Map<?, ?> m = (Map<?, ?>) o;
                String type = String.valueOf(m.get("type"));
                String title = String.valueOf(m.get("title"));
                String date = String.valueOf(m.get("date"));
                if ((!type.equals("exam") && !type.equals("contest")) || title.isBlank() || title.equals("null")) {
                    continue;
                }
                LocalDate d;
                try {
                    d = LocalDate.parse(date);
                } catch (Exception e) {
                    continue;
                }
                upsert(type, title, d, null, null,
                        String.valueOf(m.get("timeNote")),
                        String.valueOf(m.get("detail")) + "（AI 整理，仅供参考，以官方通知为准）",
                        null);
                count++;
            }
            log.info("AI 日历生成成功，{} 条", count);
            return count > 0;
        } catch (Exception e) {
            log.warn("AI 日历生成失败，回退内置知识库: {}", e.getMessage());
            return false;
        }
    }

    private String extractJsonArray(String text) {
        if (text == null) return null;
        text = text.replace("```json", "```").replace("```", "").trim();
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) return null;
        return text.substring(start, end + 1);
    }

    /** 节假日同步：timor.tech 当年 + 次年（接口返回国务院公布数据） */
    private void syncHolidays() {
        int year = LocalDate.now().getYear();
        for (int y : new int[]{year, year + 1}) {
            try {
                String body = restClient.get()
                        .uri(HOLIDAY_API + y)
                        .retrieve()
                        .body(String.class);
                if (body == null || !body.contains("\"holiday\"")) {
                    continue;
                }
                Map<?, ?> root = om.readValue(body, Map.class);
                Object holidayObj = root.get("holiday");
                if (!(holidayObj instanceof Map)) {
                    continue;
                }
                // 按 name 合并连续假期为区间
                Map<String, LocalDate[]> ranges = new LinkedHashMap<>();
                for (Map.Entry<?, ?> en : ((Map<?, ?>) holidayObj).entrySet()) {
                    if (!(en.getValue() instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> day = (Map<?, ?>) en.getValue();
                    Object isHoliday = day.get("holiday");
                    if (!Boolean.TRUE.equals(isHoliday)) {
                        continue; // holiday=false 是调休补班，不入日历
                    }
                    String name = String.valueOf(day.get("name"));
                    String dateStr = String.valueOf(day.get("date"));
                    LocalDate d;
                    try {
                        d = LocalDate.parse(dateStr);
                    } catch (Exception ex) {
                        continue;
                    }
                    LocalDate[] range = ranges.computeIfAbsent(name, k -> new LocalDate[]{d, d});
                    if (d.isBefore(range[0])) range[0] = d;
                    if (d.isAfter(range[1])) range[1] = d;
                }
                for (Map.Entry<String, LocalDate[]> en : ranges.entrySet()) {
                    upsert("holiday", en.getKey() + "假期", null, en.getValue()[0], en.getValue()[1],
                            "法定节假日（国务院公布）", null, null);
                }
            } catch (Exception e) {
                log.warn("节假日 API 同步失败 year={}: {}", y, e.getMessage());
            }
        }
    }

    /** 考试/竞赛知识库 upsert（内容不变则不动 data_version） */
    private void seedExamContests() {
        for (String[] row : EXAM_CONTEST_SEED) {
            upsert(row[0], row[1], LocalDate.parse(row[2]), null, null, row[3], row[4], null);
        }
    }

    /** 幂等 upsert：唯一键(event_type,title,event_date) 相同且内容一致则跳过 */
    private void upsert(String type, String title, LocalDate eventDate,
                        LocalDate dateStart, LocalDate dateEnd, String timeNote, String detail, String sourceUrl) {
        try {
            CampusCalendarEvent exist = eventMapper.selectOne(
                    Wrappers.<CampusCalendarEvent>lambdaQuery()
                            .eq(CampusCalendarEvent::getEventType, type)
                            .eq(CampusCalendarEvent::getTitle, title)
                            .eq(eventDate != null, CampusCalendarEvent::getEventDate, eventDate)
                            .last("LIMIT 1"));
            boolean same = exist != null
                    && eq(exist.getTimeNote(), timeNote)
                    && eq(exist.getDetail(), detail)
                    && eq(exist.getDateStart() == null ? null : exist.getDateStart().toString(), dateStart == null ? null : dateStart.toString())
                    && eq(exist.getDateEnd() == null ? null : exist.getDateEnd().toString(), dateEnd == null ? null : dateEnd.toString());
            if (same) {
                return;
            }
            if (exist == null) {
                exist = new CampusCalendarEvent();
                exist.setEventType(type);
                exist.setTitle(title);
                exist.setEventDate(eventDate);
            }
            exist.setDateStart(dateStart);
            exist.setDateEnd(dateEnd);
            exist.setTimeNote(timeNote);
            exist.setDetail(detail);
            exist.setSourceUrl(sourceUrl);
            if (exist.getId() == null) {
                eventMapper.insert(exist);
            } else {
                eventMapper.updateById(exist); // 触发 data_version ON UPDATE
            }
        } catch (Exception e) {
            log.warn("日历事件 upsert 失败 [{}{}]: {}", type, title, e.getMessage());
        }
    }

    private boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
