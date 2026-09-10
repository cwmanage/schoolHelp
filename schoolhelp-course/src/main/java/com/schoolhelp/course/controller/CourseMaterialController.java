package com.schoolhelp.course.controller;

import com.schoolhelp.common.dto.ReviewDTO;
import com.schoolhelp.common.result.Result;
import com.schoolhelp.common.util.UserContext;
import com.schoolhelp.course.entity.CourseMaterial;
import com.schoolhelp.course.service.CourseMaterialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 课程资料接口（全员可申请上传；管理员审批）
 */
@RestController
@RequestMapping("/material")
@RequiredArgsConstructor
public class CourseMaterialController {

    private final CourseMaterialService materialService;

    /** 某课程资料列表（仅已通过） */
    @GetMapping("/course/{courseId}")
    public Result<List<CourseMaterial>> listByCourse(@PathVariable Long courseId) {
        return Result.ok(materialService.listByCourse(courseId));
    }

    /** 上传资料申请（multipart: file + title + applyNote；全员） */
    @PostMapping("/course/{courseId}")
    public Result<Long> upload(@PathVariable Long courseId,
                               @RequestParam(value = "title", required = false) String title,
                               @RequestParam(value = "applyNote", required = false) String applyNote,
                               @RequestParam("file") MultipartFile file) {
        return Result.ok(materialService.upload(courseId, title, applyNote, file,
                UserContext.getUserId(), UserContext.getRole()));
    }

    /** 删除资料 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        materialService.delete(id, UserContext.getUserId(), UserContext.getRole());
        return Result.ok();
    }

    /** 我的资料申请 */
    @GetMapping("/my-applications")
    public Result<List<CourseMaterial>> myApplications() {
        return Result.ok(materialService.myApplications(UserContext.getUserId()));
    }

    /** 管理端列表 */
    @GetMapping("/admin/list")
    public Result<List<CourseMaterial>> adminList(@RequestParam(required = false) Long courseId,
                                                  @RequestParam(required = false) Integer status) {
        return Result.ok(materialService.adminList(courseId, status, UserContext.getRole()));
    }

    /** 管理端审批 */
    @PostMapping("/admin/{id}/review")
    public Result<Void> review(@PathVariable Long id, @Valid @RequestBody ReviewDTO dto) {
        materialService.review(id, dto, UserContext.getRole());
        return Result.ok();
    }
}
