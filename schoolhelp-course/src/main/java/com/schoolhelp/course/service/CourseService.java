package com.schoolhelp.course.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.schoolhelp.common.constant.CommonConstants;
import com.schoolhelp.common.dto.ReviewDTO;
import com.schoolhelp.common.exception.BusinessException;
import com.schoolhelp.course.entity.Course;
import com.schoolhelp.course.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程库服务
 * 权限（v2 审批流）：
 *  - 列表/详情：全部用户可见【已通过】课程
 *  - 申请录入：全员开放，提交后 status=0 待审批
 *  - 管理员/班长直建：班长视为免审通过？——不。v2 统一：班长录入也要走审批？班长本就是课程维护者，
 *    为兼容旧逻辑与校园现实：班长/管理员录入直接通过；普通同学录入进入待审批。
 *  - 审批：仅管理员；驳回必须写原因
 *  - 改/删：管理员任意；课程创建者/申请人本人可撤销自己的待审批申请
 */
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;

    /** 公开课程列表（已通过，可按班级/学期筛选） */
    public List<Course> list(String className, String semester) {
        return courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(className != null && !className.isEmpty(), Course::getClassName, className)
                .eq(semester != null && !semester.isEmpty(), Course::getSemester, semester)
                .eq(Course::getStatus, CommonConstants.STATUS_APPROVED)
                .orderByDesc(Course::getCreatedAt));
    }

    public Course detail(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null || course.getStatus() == null
                || course.getStatus() != CommonConstants.STATUS_APPROVED) {
            throw new BusinessException(404, "课程不存在或未通过审批");
        }
        return course;
    }

    /**
     * 录入课程申请（全员开放）
     * 班长/管理员直建 → 直接通过；普通同学 → 待审批
     */
    public Long create(Course course, Long userId, Integer role) {
        if (course == null || course.getName() == null || course.getName().trim().isEmpty()) {
            throw new BusinessException(400, "课程名不能为空");
        }
        course.setId(null);
        course.setCreatorId(userId);
        course.setApplyUserId(userId);
        boolean privileged = role != null && (role == CommonConstants.ROLE_MONITOR || role == CommonConstants.ROLE_ADMIN);
        if (privileged) {
            // 班长/管理员直建直接通过（历史行为兼容）
            course.setStatus(CommonConstants.STATUS_APPROVED);
            course.setReviewNote("管理员/班长录入，自动通过");
            course.setReviewedAt(LocalDateTime.now());
        } else {
            course.setStatus(CommonConstants.STATUS_PENDING);
        }
        courseMapper.insert(course);
        return course.getId();
    }

    /** 管理员审批：action=1通过 / action=2驳回(需原因) */
    public void review(Long id, ReviewDTO dto, Integer role) {
        requireAdmin(role);
        Course exist = requireExists(id);
        if (exist.getStatus() != CommonConstants.STATUS_PENDING) {
            throw new BusinessException(400, "该课程不在待审批状态");
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
        courseMapper.updateById(exist);
    }

    /** 修改：管理员/班长可改并保持原状态；普通同学改后一律回到待审批，需重新审核 */
    public void update(Long id, Course course, Long userId, Integer role) {
        Course exist = requireEditable(id, userId, role);
        exist.setName(course.getName());
        exist.setTeacherName(course.getTeacherName());
        exist.setTeacherLink(course.getTeacherLink());
        exist.setClassName(course.getClassName());
        exist.setSemester(course.getSemester());
        exist.setDescription(course.getDescription());
        // 非特权角色（普通同学）编辑后一律回到待审批，重新走审批
        // （防止同学对已通过(1)的课程改内容绕过审核）；班长/管理员为课程维护者，保持原状态
        boolean privileged = role != null && (role == CommonConstants.ROLE_MONITOR || role == CommonConstants.ROLE_ADMIN);
        if (!privileged) {
            exist.setStatus(CommonConstants.STATUS_PENDING);
            exist.setReviewNote(null);
            exist.setReviewedAt(null);
        }
        courseMapper.updateById(exist);
    }

    /** 删除：管理员任意；创建者删自己的待审批/驳回申请；已通过的课程仅管理员可下架删除 */
    public void delete(Long id, Long userId, Integer role) {
        Course exist = requireExists(id);
        boolean isAdmin = role != null && role == CommonConstants.ROLE_ADMIN;
        boolean isOwner = exist.getCreatorId() != null && exist.getCreatorId().equals(userId);
        boolean isApplyer = exist.getApplyUserId() != null && exist.getApplyUserId().equals(userId);
        boolean approved = exist.getStatus() != null && exist.getStatus() == CommonConstants.STATUS_APPROVED;
        if (isAdmin) {
            courseMapper.deleteById(id);
            return;
        }
        // 非管理员：仅能删自己未通过审批的申请
        if ((isOwner || isApplyer) && !approved) {
            courseMapper.deleteById(id);
            return;
        }
        throw new BusinessException(403, "无权限删除该课程");
    }

    /** 我的申请列表（申请人视角：待审批/已驳回/已通过都可见，用于跟踪） */
    public List<Course> myApplications(Long userId) {
        return courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(Course::getApplyUserId, userId)
                .orderByDesc(Course::getCreatedAt));
    }

    /** 管理端：待审批列表 / 全部含驳回（admin） */
    public List<Course> adminList(Integer status, Integer role) {
        requireAdmin(role);
        return courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(status != null, Course::getStatus, status)
                .orderByAsc(Course::getStatus)
                .orderByDesc(Course::getCreatedAt));
    }

    /** 校验管理员 */
    private void requireAdmin(Integer role) {
        if (role == null || role != CommonConstants.ROLE_ADMIN) {
            throw new BusinessException(403, "仅管理员可审批");
        }
    }

    private Course requireExists(Long id) {
        Course exist = courseMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(404, "课程不存在");
        }
        return exist;
    }

    /** 校验可编辑：管理员任意；创建者或申请人本人可编辑 */
    private Course requireEditable(Long id, Long userId, Integer role) {
        Course exist = requireExists(id);
        boolean isAdmin = role != null && role == CommonConstants.ROLE_ADMIN;
        boolean isOwner = exist.getCreatorId() != null && exist.getCreatorId().equals(userId);
        boolean isApplyer = exist.getApplyUserId() != null && exist.getApplyUserId().equals(userId);
        if (!isAdmin && !isOwner && !isApplyer) {
            throw new BusinessException(403, "只能操作自己创建/申请的课程");
        }
        return exist;
    }
}
