package com.schoolhelp.course.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.schoolhelp.common.constant.CommonConstants;
import com.schoolhelp.common.dto.ReviewDTO;
import com.schoolhelp.common.exception.BusinessException;
import com.schoolhelp.course.entity.Course;
import com.schoolhelp.course.entity.CourseMaterial;
import com.schoolhelp.course.mapper.CourseMapper;
import com.schoolhelp.course.mapper.CourseMaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 课程资料服务（v2 审批流）：文件存本地/NAS，DB 存路径
 *  - 公开列表：仅已通过
 *  - 上传：全员开放（需选已通过课程）；班长/管理员直传通过，同学走审批
 *  - 审批：管理员
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseMaterialService {

    private final CourseMaterialMapper materialMapper;
    private final CourseMapper courseMapper;

    @Value("${schoolhelp.file.upload-dir}")
    private String uploadDir;

    @Value("${schoolhelp.file.base-url:/files}")
    private String baseUrl;

    private static final long MAX_SIZE = 200 * 1024 * 1024; // 200MB

    /** 某课程资料列表（仅已通过） */
    public List<CourseMaterial> listByCourse(Long courseId) {
        return materialMapper.selectList(Wrappers.<CourseMaterial>lambdaQuery()
                .eq(CourseMaterial::getCourseId, courseId)
                .eq(CourseMaterial::getStatus, CommonConstants.STATUS_APPROVED)
                .orderByDesc(CourseMaterial::getCreatedAt));
    }

    /** 上传资料申请（全员）：班长/管理员直传通过，同学待审批 */
    public Long upload(Long courseId, String title, String applyNote, MultipartFile file, Long userId, Integer role) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }
        if (course.getStatus() == null || course.getStatus() != CommonConstants.STATUS_APPROVED) {
            throw new BusinessException(400, "课程未通过审批，无法上传资料");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(400, "文件不能超过200MB");
        }

        String original = file.getOriginalFilename();
        String ext = original == null ? "" :
                original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        String dir = uploadDir + "/material/" + courseId;
        try {
            Files.createDirectories(Paths.get(dir));
        } catch (IOException e) {
            throw new BusinessException(500, "创建目录失败");
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            file.transferTo(Paths.get(dir, filename).toAbsolutePath());
        } catch (IOException e) {
            log.error("资料保存失败", e);
            throw new BusinessException(500, "资料保存失败");
        }

        CourseMaterial m = new CourseMaterial();
        m.setCourseId(courseId);
        m.setTitle(title == null || title.isEmpty() ? original : title);
        m.setFileName(original);
        m.setFilePath(baseUrl + "/material/" + courseId + "/" + filename);
        m.setFileSize(file.getSize());
        m.setFileType(ext.isEmpty() ? null : ext);
        m.setUploaderId(userId);
        m.setApplyUserId(userId);
        if (applyNote != null && !applyNote.trim().isEmpty()) {
            m.setApplyNote(applyNote.trim());
        }
        boolean privileged = role != null && (role == CommonConstants.ROLE_MONITOR || role == CommonConstants.ROLE_ADMIN);
        if (privileged) {
            m.setStatus(CommonConstants.STATUS_APPROVED);
            m.setReviewNote("班长/管理员上传，自动通过");
            m.setReviewedAt(LocalDateTime.now());
        } else {
            m.setStatus(CommonConstants.STATUS_PENDING);
        }
        materialMapper.insert(m);
        return m.getId();
    }

    /** 删除资料：管理员任意；上传人删自己未通过的申请 */
    public void delete(Long id, Long userId, Integer role) {
        CourseMaterial exist = materialMapper.selectById(id);
        if (exist == null) {
            return;
        }
        boolean isAdmin = role != null && role == CommonConstants.ROLE_ADMIN;
        boolean isUploader = exist.getUploaderId() != null && exist.getUploaderId().equals(userId);
        boolean approved = exist.getStatus() != null && exist.getStatus() == CommonConstants.STATUS_APPROVED;
        if (isAdmin) {
            deleteFile(exist);
            materialMapper.deleteById(id);
            return;
        }
        if (isUploader && !approved) {
            deleteFile(exist);
            materialMapper.deleteById(id);
            return;
        }
        throw new BusinessException(403, "无权限删除该资料");
    }

    /** 管理员审批 */
    public void review(Long id, ReviewDTO dto, Integer role) {
        requireAdmin(role);
        CourseMaterial exist = materialMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(404, "资料不存在");
        }
        if (exist.getStatus() != CommonConstants.STATUS_PENDING) {
            throw new BusinessException(400, "该资料不在待审批状态");
        }
        Integer action = dto.getAction();
        if (action == null || (action != CommonConstants.STATUS_APPROVED && action != CommonConstants.STATUS_REJECTED)) {
            throw new BusinessException(400, "审批动作不合法");
        }
        String note = dto.getNote() == null ? "" : dto.getNote().trim();
        if (action == CommonConstants.STATUS_REJECTED && note.isEmpty()) {
            throw new BusinessException(400, "驳回必须填写原因");
        }
        exist.setStatus(action);
        exist.setReviewNote(note);
        exist.setReviewedAt(LocalDateTime.now());
        materialMapper.updateById(exist);
    }

    /** 我的资料申请 */
    public List<CourseMaterial> myApplications(Long userId) {
        return materialMapper.selectList(Wrappers.<CourseMaterial>lambdaQuery()
                .eq(CourseMaterial::getApplyUserId, userId)
                .orderByDesc(CourseMaterial::getCreatedAt));
    }

    /** 管理端列表（按状态筛选） */
    public List<CourseMaterial> adminList(Long courseId, Integer status, Integer role) {
        requireAdmin(role);
        return materialMapper.selectList(Wrappers.<CourseMaterial>lambdaQuery()
                .eq(courseId != null, CourseMaterial::getCourseId, courseId)
                .eq(status != null, CourseMaterial::getStatus, status)
                .orderByAsc(CourseMaterial::getStatus)
                .orderByDesc(CourseMaterial::getCreatedAt));
    }

    private void deleteFile(CourseMaterial exist) {
        try {
            String dir = uploadDir + "/material/" + exist.getCourseId();
            String path = exist.getFilePath();
            if (path != null && path.contains("/")) {
                String fn = path.substring(path.lastIndexOf('/') + 1);
                Files.deleteIfExists(Paths.get(dir, fn));
            }
        } catch (IOException e) {
            log.warn("删除资料文件失败: {}", e.getMessage());
        }
    }

    private void requireAdmin(Integer role) {
        if (role == null || role != CommonConstants.ROLE_ADMIN) {
            throw new BusinessException(403, "仅管理员可审批");
        }
    }
}
