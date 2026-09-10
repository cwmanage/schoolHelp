package com.schoolhelp.course.controller;

import com.schoolhelp.common.dto.ReviewDTO;
import com.schoolhelp.common.result.Result;
import com.schoolhelp.common.util.UserContext;
import com.schoolhelp.course.entity.Course;
import com.schoolhelp.course.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程库接口（全员可申请录入；管理员审批；同学查看）
 */
@RestController
@RequestMapping("/course")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    /** 公开课程列表（已通过，可按班级/学期筛选） */
    @GetMapping("/list")
    public Result<List<Course>> list(@RequestParam(required = false) String className,
                                     @RequestParam(required = false) String semester) {
        return Result.ok(courseService.list(className, semester));
    }

    /** 课程详情（学生点课表课程跳这里） */
    @GetMapping("/{id}")
    public Result<Course> detail(@PathVariable Long id) {
        return Result.ok(courseService.detail(id));
    }

    /** 申请录入课程（全员开放；班长/管理员直建直接通过） */
    @PostMapping
    public Result<Long> create(@RequestBody Course course) {
        return Result.ok(courseService.create(course, UserContext.getUserId(), UserContext.getRole()));
    }

    /** 修改课程（管理员任意；创建者/申请人本人改自己的申请） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Course course) {
        courseService.update(id, course, UserContext.getUserId(), UserContext.getRole());
        return Result.ok();
    }

    /** 删除课程（管理员任意；创建者删自己未通过的申请） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        courseService.delete(id, UserContext.getUserId(), UserContext.getRole());
        return Result.ok();
    }

    /** 我的课程申请（本人跟踪审批进度） */
    @GetMapping("/my-applications")
    public Result<List<Course>> myApplications() {
        return Result.ok(courseService.myApplications(UserContext.getUserId()));
    }

    /** 管理端：课程列表按状态筛选（待审批=0 / 已通过=1 / 已驳回=2） */
    @GetMapping("/admin/list")
    public Result<List<Course>> adminList(@RequestParam(required = false) Integer status) {
        return Result.ok(courseService.adminList(status, UserContext.getRole()));
    }

    /** 管理端审批：POST /course/admin/{id}/review  body={action:1|2, note} */
    @PostMapping("/admin/{id}/review")
    public Result<Void> review(@PathVariable Long id, @Valid @RequestBody ReviewDTO dto) {
        courseService.review(id, dto, UserContext.getRole());
        return Result.ok();
    }
}
