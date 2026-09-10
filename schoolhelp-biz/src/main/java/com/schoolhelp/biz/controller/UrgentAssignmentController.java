package com.schoolhelp.biz.controller;

import com.schoolhelp.common.result.Result;
import com.schoolhelp.common.util.UserContext;
import com.schoolhelp.biz.service.UrgentAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 临期作业接口（首页红色条幅滚动数据源）
 */
@RestController
@RequestMapping("/urgent")
@RequiredArgsConstructor
public class UrgentAssignmentController {

    private final UrgentAssignmentService urgentService;

    /**
     * 5天内要交的作业（仅当前学期课表中的、未提交的）
     */
    @GetMapping("/assignments")
    public Result<List<Map<String, Object>>> urgentAssignments(
            @RequestParam(required = false) String semester) {
        return Result.ok(urgentService.urgentAssignments(UserContext.getUserId(), semester));
    }
}
