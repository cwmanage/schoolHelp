package com.schoolhelp.user.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolhelp.user.agent.AgentTool;
import com.schoolhelp.user.agent.ToolParam;
import com.schoolhelp.user.service.WeatherLocateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 天气工具：定位（默认哈尔滨学院）+ Open-Meteo 实时天气与 3 天预报。
 * 只返回原始天气数据，穿衣建议等由 LLM 自行组合生成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherTools {

    private final WeatherLocateService locateService;
    private final ObjectMapper objectMapper;
    private final RestClient openMeteo = RestClient.builder()
            .baseUrl("https://api.open-meteo.com/v1/forecast")
            .requestFactory(simpleFactory())
            .build();

    @AgentTool(name = "get_weather",
            description = "查询当前所在城市的实时天气（温度/体感/风速/天气现象）与未来3天预报。"
                    + "当用户询问天气、冷热、穿衣建议、出行时使用。")
    public Map<String, Object> getWeather(
            @ToolParam(name = "ignore", description = "占位参数，固定传空字符串即可", required = false)
            String ignore) {
        try {
            // 第一版固定学院坐标（工具在请求线程内，可扩展为按请求 IP 智能定位）
            Map<String, Object> loc = locateService.locate("");
            double lat = ((Number) loc.get("lat")).doubleValue();
            double lon = ((Number) loc.get("lon")).doubleValue();

            JsonNode data = openMeteo.get()
                    .uri(b -> b.queryParam("latitude", lat).queryParam("longitude", lon)
                            .queryParam("current", "temperature_2m,apparent_temperature,weather_code,wind_speed_10m")
                            .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min")
                            .queryParam("timezone", "Asia/Shanghai")
                            .queryParam("forecast_days", 3)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode cur = data.path("current");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("city", loc.get("name"));
            out.put("temperature", cur.path("temperature_2m").asDouble());
            out.put("feelsLike", cur.path("apparent_temperature").asDouble());
            out.put("windSpeed", cur.path("wind_speed_10m").asDouble());
            out.put("weatherCode", cur.path("weather_code").asInt());
            out.put("weatherText", weatherText(cur.path("weather_code").asInt()));

            List<Map<String, Object>> daily = new java.util.ArrayList<>();
            JsonNode days = data.path("daily");
            if (days.path("time").isArray()) {
                for (int i = 0; i < days.path("time").size(); i++) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("date", days.path("time").get(i).asText());
                    d.put("max", days.path("temperature_2m_max").get(i).asDouble());
                    d.put("min", days.path("temperature_2m_min").get(i).asDouble());
                    d.put("weatherText", weatherText(days.path("weather_code").get(i).asInt()));
                    daily.add(d);
                }
            }
            out.put("daily", daily);
            return out;
        } catch (Exception e) {
            log.warn("天气工具执行失败: {}", e.getMessage());
            return Map.of("error", "天气查询失败：" + e.getMessage());
        }
    }

    /** WMO 天气代码 -> 中文描述（与前端 WeatherPanel 的映射保持一致口径） */
    private String weatherText(int code) {
        return switch (code) {
            case 0 -> "晴";
            case 1 -> "大部晴朗";
            case 2 -> "多云";
            case 3 -> "阴";
            case 45, 48 -> "雾";
            case 51, 53, 55 -> "毛毛雨";
            case 56, 57 -> "冻雨";
            case 61 -> "小雨";
            case 63 -> "中雨";
            case 65, 66, 67 -> "大雨";
            case 71 -> "小雪";
            case 73 -> "中雪";
            case 75, 77 -> "大雪";
            case 80, 81 -> "阵雨";
            case 82 -> "强阵雨";
            case 85, 86 -> "阵雪";
            case 95 -> "雷阵雨";
            case 96, 99 -> "雷阵雨伴冰雹";
            default -> "未知";
        };
    }

    private static SimpleClientHttpRequestFactory simpleFactory() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(5));
        f.setReadTimeout(Duration.ofSeconds(10));
        return f;
    }
}
