package com.schoolhelp.activity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 大学活动分析服务（v5 拆分）：校园日历（考试/竞赛/节假日/活动）同步、查询与已读状态。
 * 高内聚：与「活动」相关的接口/方法/定时任务全部收敛在本服务。
 */
@SpringBootApplication(scanBasePackages = {"com.schoolhelp"})
@EnableDiscoveryClient
@EnableScheduling
@org.mybatis.spring.annotation.MapperScan("com.schoolhelp.activity.mapper")
public class ActivityApplication {
    public static void main(String[] args) {
        SpringApplication.run(ActivityApplication.class, args);
    }
}
