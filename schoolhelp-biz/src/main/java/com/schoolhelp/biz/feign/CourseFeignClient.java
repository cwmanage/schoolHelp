package com.schoolhelp.biz.feign;

import com.schoolhelp.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

/**
 * 调用 course-service：作业 + 课程
 */
@FeignClient(name = "schoolhelp-course")
public interface CourseFeignClient {

    /** 某课程作业列表（含 deadline） */
    @GetMapping("/assignment/course/{courseId}")
    Result<List<Map<String, Object>>> assignmentsByCourse(@PathVariable("courseId") Long courseId);

    /** 课程详情 */
    @GetMapping("/course/{id}")
    Result<Map<String, Object>> courseDetail(@PathVariable("id") Long id);

    /** 作业详情（用于校验作业存在/已通过，避免对已删作业标记提交） */
    @GetMapping("/assignment/{id}")
    Result<Map<String, Object>> assignmentDetail(@PathVariable("id") Long id);
}
