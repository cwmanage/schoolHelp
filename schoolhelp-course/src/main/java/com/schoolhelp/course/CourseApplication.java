package com.schoolhelp.course;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * schoolHelp 课程服务 (8102)
 */
@SpringBootApplication(scanBasePackages = {"com.schoolhelp"})
@EnableDiscoveryClient
@MapperScan("com.schoolhelp.course.mapper")
public class CourseApplication {
    public static void main(String[] args) {
        SpringApplication.run(CourseApplication.class, args);
    }
}
