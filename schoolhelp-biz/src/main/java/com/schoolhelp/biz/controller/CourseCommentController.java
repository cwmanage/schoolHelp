package com.schoolhelp.biz.controller;

import com.schoolhelp.common.result.Result;
import com.schoolhelp.common.util.UserContext;
import com.schoolhelp.biz.entity.CourseComment;
import com.schoolhelp.biz.service.CourseCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程评论接口（匿名评论对同学保密，管理员可查真实身份）
 */
@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CourseCommentController {

    private final CourseCommentService commentService;

    /** 某课程评论列表 */
    @GetMapping("/course/{courseId}")
    public Result<List<CourseComment>> list(@PathVariable Long courseId) {
        boolean isAdmin = UserContext.getRole() != null
                && UserContext.getRole() == com.schoolhelp.common.constant.CommonConstants.ROLE_ADMIN;
        return Result.ok(commentService.listByCourse(courseId, isAdmin));
    }

    /** 发表评论：body {content, isAnonymous} */
    @PostMapping("/course/{courseId}")
    public Result<Long> add(@PathVariable Long courseId, @RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        Integer isAnonymous = body.get("isAnonymous") == null ? 0 :
                ((Boolean) body.get("isAnonymous") ? 1 : 0);
        return Result.ok(commentService.add(courseId, UserContext.getUserId(), content, isAnonymous));
    }

    /** 删除（管理员任意/自己） */
    @DeleteMapping("/{commentId}")
    public Result<Void> delete(@PathVariable Long commentId) {
        commentService.delete(commentId, UserContext.getUserId(), UserContext.getRole());
        return Result.ok();
    }

    /** 管理员查看评论真实身份（匿名评论的 user_id 在此可见） */
    @GetMapping("/admin/{commentId}")
    public Result<Map<String, Object>> adminDetail(@PathVariable Long commentId) {
        if (UserContext.getRole() == null
                || UserContext.getRole() != com.schoolhelp.common.constant.CommonConstants.ROLE_ADMIN) {
            return Result.fail(403, "仅管理员可查看真实身份");
        }
        CourseComment c = commentService.adminDetail(commentId);
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("courseId", c.getCourseId());
        m.put("userId", c.getUserId());   // 真实用户
        m.put("content", c.getContent());
        m.put("isAnonymous", c.getIsAnonymous());
        m.put("createdAt", c.getCreatedAt());
        return Result.ok(m);
    }
}
