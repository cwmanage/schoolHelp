package com.schoolhelp.biz.feign;

import com.schoolhelp.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 调用 user-service 拿我的课表（临期作业聚合用）
 */
@FeignClient(name = "schoolhelp-user")
public interface UserFeignClient {

    /**
     * 我的课表（返回 schedule 列表，含 courseId）
     * 网关已把 X-User-Id 放行，但 Feign 直接走服务注册中心，需显式传 userId
     */
    @GetMapping("/schedule/list-by-user")
    Result<List<Map<String, Object>>> mySchedules(@RequestParam("userId") Long userId,
                                                  @RequestParam(value = "semester", required = false) String semester);
}
