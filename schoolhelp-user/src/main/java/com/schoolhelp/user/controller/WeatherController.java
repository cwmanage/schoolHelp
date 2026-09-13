package com.schoolhelp.user.controller;

import com.schoolhelp.common.result.Result;
import com.schoolhelp.user.service.WeatherLocateService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 天气定位（默认哈尔滨学院；用户 IP 距学院较远时自动切换为用户位置）
 * 加白名单：游客打开天气面板也可用
 */
@RestController
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherLocateService weatherLocateService;

    /** 按客户端真实 IP 智能定位：{lat, lon, name, isCampus} */
    @GetMapping("/weather/locate")
    public Result<Map<String, Object>> locate(HttpServletRequest request) {
        String ip = weatherLocateService.extractClientIp(
                request.getHeader("X-Real-IP"),
                request.getHeader("X-Forwarded-For"),
                request.getRemoteAddr());
        return Result.ok(weatherLocateService.locate(ip));
    }
}
