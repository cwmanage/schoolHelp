package com.schoolhelp.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * schoolHelp 用户服务 (8101)
 */
@SpringBootApplication(scanBasePackages = {"com.schoolhelp"})
@EnableDiscoveryClient
@MapperScan("com.schoolhelp.user.mapper")
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
