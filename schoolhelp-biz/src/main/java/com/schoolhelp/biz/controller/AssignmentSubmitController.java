package com.schoolhelp.biz.controller;

import com.schoolhelp.common.result.Result;
import com.schoolhelp.common.util.UserContext;
import com.schoolhelp.biz.service.AssignmentSubmitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 作业提交标记接口
 */
@RestController
@RequestMapping("/assignment")
@RequiredArgsConstructor
public class AssignmentSubmitController {

    private final AssignmentSubmitService submitService;

    /** 是否已提交 */
    @GetMapping("/{assignmentId}/submitted")
    public Result<Boolean> submitted(@PathVariable Long assignmentId) {
        return Result.ok(submitService.submitted(assignmentId, UserContext.getUserId()));
    }

    /** 标记已提交 */
    @PostMapping("/{assignmentId}/submit")
    public Result<Void> markSubmitted(@PathVariable Long assignmentId) {
        submitService.markSubmitted(assignmentId, UserContext.getUserId());
        return Result.ok();
    }

    /** 取消已提交标记 */
    @DeleteMapping("/{assignmentId}/submit")
    public Result<Void> unmark(@PathVariable Long assignmentId) {
        submitService.unmark(assignmentId, UserContext.getUserId());
        return Result.ok();
    }
}
