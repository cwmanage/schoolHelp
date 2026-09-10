package com.schoolhelp.course.controller;

import com.schoolhelp.common.dto.ReviewDTO;
import com.schoolhelp.common.result.Result;
import com.schoolhelp.common.util.UserContext;
import com.schoolhelp.course.entity.Assignment;
import com.schoolhelp.course.service.AssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 作业接口（全员可申请发布；管理员审批；同学查看）
 */
@RestController
@RequestMapping("/assignment")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    /** 某课程作业列表（仅已通过） */
    @GetMapping("/course/{courseId}")
    public Result<List<Assignment>> listByCourse(@PathVariable Long courseId) {
        return Result.ok(assignmentService.listByCourse(courseId));
    }

    /** 作业详情 */
    @GetMapping("/{id}")
    public Result<Assignment> detail(@PathVariable Long id) {
        return Result.ok(assignmentService.detail(id));
    }

    /** 发布作业申请（全员；班长/管理员直发通过） */
    @PostMapping
    public Result<Long> create(@RequestBody Assignment assignment) {
        return Result.ok(assignmentService.create(assignment, UserContext.getUserId(), UserContext.getRole()));
    }

    /** 修改作业 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Assignment assignment) {
        assignmentService.update(id, assignment, UserContext.getUserId(), UserContext.getRole());
        return Result.ok();
    }

    /** 撤销作业 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        assignmentService.delete(id, UserContext.getUserId(), UserContext.getRole());
        return Result.ok();
    }

    /** 我的作业申请 */
    @GetMapping("/my-applications")
    public Result<List<Assignment>> myApplications() {
        return Result.ok(assignmentService.myApplications(UserContext.getUserId()));
    }

    /** 管理端列表（按课程/状态筛选） */
    @GetMapping("/admin/list")
    public Result<List<Assignment>> adminList(@RequestParam(required = false) Long courseId,
                                              @RequestParam(required = false) Integer status) {
        return Result.ok(assignmentService.adminList(courseId, status, UserContext.getRole()));
    }

    /** 管理端审批 */
    @PostMapping("/admin/{id}/review")
    public Result<Void> review(@PathVariable Long id, @Valid @RequestBody ReviewDTO dto) {
        assignmentService.review(id, dto, UserContext.getRole());
        return Result.ok();
    }
}
