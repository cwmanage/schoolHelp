package com.schoolhelp.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * schoolHelp 业务服务 (8103)
 */
@SpringBootApplication(scanBasePackages = {"com.schoolhelp"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.schoolhelp.biz.feign")
@MapperScan("com.schoolhelp.biz.mapper")
public class BizApplication {
    public static void main(String[] args) {
        SpringApplication.run(BizApplication.class, args);
    }
}
