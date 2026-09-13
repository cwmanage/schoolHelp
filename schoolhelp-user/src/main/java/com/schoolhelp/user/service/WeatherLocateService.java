package com.schoolhelp.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 天气定位服务：
 * - 默认哈尔滨学院坐标
 * - 按客户端真实 IP 调免费 IP 定位（ip-api.com，中文），距离学院 > 30km 时自动切换为用户位置
 * - 内网/回环/定位失败一律回退学院坐标；同 IP 结果缓存 5 分钟
 * 说明：站点当前为 HTTP（非安全上下文），浏览器 Geolocation 不可用，故采用 IP 定位；
 *      将来启用 HTTPS 后可升级为浏览器精确定位。
 */
@Slf4j
@Service
public class WeatherLocateService {

    /** 哈尔滨学院（哈尔滨市南岗区中兴大道109号片区，与前端 WeatherPanel 原写死坐标一致） */
    private static final double CAMPUS_LAT = 45.716;
    private static final double CAMPUS_LON = 126.59;
    private static final String CAMPUS_NAME = "哈尔滨学院";
    /** 距离阈值（km）：超过则视为不在学院附近 */
    private static final double NEAR_KM = 30.0;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final ObjectMapper om = new ObjectMapper();

    /** ip → 定位结果缓存（5 分钟），避免免费接口限流 */
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private record CacheEntry(Map<String, Object> result, long at) {}

    /** 定位结果：{lat, lon, name, isCampus} */
    public Map<String, Object> locate(String clientIp) {
        String ip = normalizeIp(clientIp);
        if (isPrivateOrLoopback(ip)) {
            return campus();
        }
        long now = System.currentTimeMillis();
        CacheEntry hit = cache.get(ip);
        if (hit != null && now - hit.at() < 5 * 60 * 1000L) {
            return hit.result();
        }
        Map<String, Object> result = locateByIpApi(ip);
        if (result == null) {
            result = campus();
        }
        cache.put(ip, new CacheEntry(result, now));
        return result;
    }

    /** 调 ip-api.com 免费接口（HTTP，中文）；失败/解析失败返回 null */
    private Map<String, Object> locateByIpApi(String ip) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://ip-api.com/json/" + ip + "?lang=zh-CN&fields=status,regionName,city,lat,lon"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return null;
            }
            Map<?, ?> m = om.readValue(resp.body(), Map.class);
            if (!"success".equals(String.valueOf(m.get("status")))) {
                return null;
            }
            double lat = ((Number) m.get("lat")).doubleValue();
            double lon = ((Number) m.get("lon")).doubleValue();
            String region = str(m.get("regionName"));
            String city = str(m.get("city"));
            String name = (region.isEmpty() ? "" : region) + (city.isEmpty() ? "" : " · " + city);
            if (name.isBlank() || lat == 0 || lon == 0) {
                return null;
            }
            double dist = haversineKm(lat, lon, CAMPUS_LAT, CAMPUS_LON);
            if (dist <= NEAR_KM) {
                return campus(); // 学院附近 → 用学院
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("lat", lat);
            out.put("lon", lon);
            out.put("name", name);
            out.put("isCampus", false);
            return out;
        } catch (Exception e) {
            log.warn("IP 定位失败 ip={}: {}", ip, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> campus() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lat", CAMPUS_LAT);
        out.put("lon", CAMPUS_LON);
        out.put("name", CAMPUS_NAME);
        out.put("isCampus", true);
        return out;
    }

    /** 提取真实客户端 IP：优先 X-Real-IP / X-Forwarded-For 首段 */
    public String extractClientIp(String xRealIp, String xForwardedFor, String remoteAddr) {
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return remoteAddr == null ? "" : remoteAddr.replace("/", "").split(":")[0];
    }

    private String normalizeIp(String ip) {
        if (ip == null) return "";
        ip = ip.trim();
        // IPv6 环回与映射前缀
        if (ip.startsWith("0:0:0:0:0:0:0:1") || ip.equals("::1")) return "127.0.0.1";
        if (ip.startsWith("::ffff:")) ip = ip.substring(7);
        return ip;
    }

    private boolean isPrivateOrLoopback(String ip) {
        if (ip == null || ip.isBlank()) return true;
        if (ip.equals("127.0.0.1") || ip.startsWith("127.")) return true;
        if (ip.startsWith("10.") || ip.startsWith("192.168.")) return true;
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                return second >= 16 && second <= 31;
            } catch (Exception e) {
                return true;
            }
        }
        try {
            return InetAddress.getByName(ip).isSiteLocalAddress();
        } catch (Exception e) {
            return true;
        }
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
