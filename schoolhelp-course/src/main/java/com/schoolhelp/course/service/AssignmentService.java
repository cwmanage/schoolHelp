package com.schoolhelp.course.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.schoolhelp.common.constant.CommonConstants;
import com.schoolhelp.common.dto.ReviewDTO;
import com.schoolhelp.common.exception.BusinessException;
import com.schoolhelp.course.entity.Assignment;
import com.schoolhelp.course.entity.Course;
import com.schoolhelp.course.mapper.AssignmentMapper;
import com.schoolhelp.course.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 作业服务（v2 审批流）
 *  - 作业列表：仅展示【已通过】且关联课程存在
 *  - 发布申请：全员开放（需选已通过课程）；班长/管理员直发直接通过，同学走审批
 *  - 审批：管理员
 */
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentMapper assignmentMapper;
    private final CourseMapper courseMapper;

    /** 某课程的作业列表（仅已通过） */
    public List<Assignment> listByCourse(Long courseId) {
        return assignmentMapper.selectList(Wrappers.<Assignment>lambdaQuery()
                .eq(Assignment::getCourseId, courseId)
                .eq(Assignment::getStatus, CommonConstants.STATUS_APPROVED)
                .orderByDesc(Assignment::getDeadline));
    }

    public Assignment detail(Long id) {
        Assignment a = assignmentMapper.selectById(id);
        if (a == null || a.getStatus() == null || a.getStatus() != CommonConstants.STATUS_APPROVED) {
            throw new BusinessException(404, "作业不存在或未通过审批");
        }
        return a;
    }

    /** 发布作业申请（全员开放）：班长/管理员直发通过；同学待审批 */
    public Long create(Assignment assignment, Long userId, Integer role) {
        if (assignment.getCourseId() == null) {
            throw new BusinessException(400, "请选择所属课程");
        }
        Course course = courseMapper.selectById(assignment.getCourseId());
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }
        if (course.getStatus() == null || course.getStatus() != CommonConstants.STATUS_APPROVED) {
            throw new BusinessException(400, "课程未通过审批，无法发布作业");
        }
        if (assignment.getTitle() == null || assignment.getTitle().trim().isEmpty()) {
            throw new BusinessException(400, "作业标题不能为空");
        }
        if (assignment.getDeadline() == null) {
            throw new BusinessException(400, "请设置作业截止时间");
        }
        assignment.setId(null);
        assignment.setCreatorId(userId);
        assignment.setApplyUserId(userId);
        boolean privileged = role != null && (role == CommonConstants.ROLE_MONITOR || role == CommonConstants.ROLE_ADMIN);
        if (privileged) {
            assignment.setStatus(CommonConstants.STATUS_APPROVED);
            assignment.setReviewNote("班长/管理员发布，自动通过");
            assignment.setReviewedAt(LocalDateTime.now());
        } else {
            assignment.setStatus(CommonConstants.STATUS_PENDING);
        }
        assignmentMapper.insert(assignment);
        return assignment.getId();
    }

    /** 修改作业：管理员/班长可改并保持原状态；普通同学改后一律回到待审批，需重新审核 */
    public void update(Long id, Assignment assignment, Long userId, Integer role) {
        Assignment exist = requireExists(id);
        checkEditable(exist, userId, role);
        exist.setTitle(assignment.getTitle());
        exist.setContent(assignment.getContent());
        exist.setDeadline(assignment.getDeadline());
        // 非特权角色（普通同学）编辑后一律回到待审批，重新走审批
        // （防止同学对已通过(1)的作业改内容绕过审核）；班长/管理员保持原状态
        boolean privileged = role != null && (role == CommonConstants.ROLE_MONITOR || role == CommonConstants.ROLE_ADMIN);
        if (!privileged) {
            exist.setStatus(CommonConstants.STATUS_PENDING);
            exist.setReviewNote(null);
            exist.setReviewedAt(null);
        }
        assignmentMapper.updateById(exist);
    }

    /** 撤销作业：管理员任意；发布人/申请人删自己的待审或驳回申请；已通过作业仅管理员可撤 */
    public void delete(Long id, Long userId, Integer role) {
        Assignment exist = assignmentMapper.selectById(id);
        if (exist == null) {
            return;
        }
        boolean isAdmin = role != null && role == CommonConstants.ROLE_ADMIN;
        boolean isOwner = exist.getCreatorId() != null && exist.getCreatorId().equals(userId);
        boolean approved = exist.getStatus() != null && exist.getStatus() == CommonConstants.STATUS_APPROVED;
        if (isAdmin) {
            assignmentMapper.deleteById(id);
            return;
        }
        if (isOwner && !approved) {
            assignmentMapper.deleteById(id);
            return;
        }
        throw new BusinessException(403, "无权限删除该作业");
    }

    /** 管理员审批 */
    public void review(Long id, ReviewDTO dto, Integer role) {
        requireAdmin(role);
        Assignment exist = requireExists(id);
        if (exist.getStatus() != CommonConstants.STATUS_PENDING) {
            throw new BusinessException(400, "该作业不在待审批状态");
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
        assignmentMapper.updateById(exist);
    }

    /** 我的作业申请 */
    public List<Assignment> myApplications(Long userId) {
        return assignmentMapper.selectList(Wrappers.<Assignment>lambdaQuery()
                .eq(Assignment::getApplyUserId, userId)
                .orderByDesc(Assignment::getCreatedAt));
    }

    /** 管理端列表（按状态筛选） */
    public List<Assignment> adminList(Long courseId, Integer status, Integer role) {
        requireAdmin(role);
        return assignmentMapper.selectList(Wrappers.<Assignment>lambdaQuery()
                .eq(courseId != null, Assignment::getCourseId, courseId)
                .eq(status != null, Assignment::getStatus, status)
                .orderByAsc(Assignment::getStatus)
                .orderByDesc(Assignment::getCreatedAt));
    }

    private Assignment requireExists(Long id) {
        Assignment exist = assignmentMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(404, "作业不存在");
        }
        return exist;
    }

    private void checkEditable(Assignment exist, Long userId, Integer role) {
        boolean isAdmin = role != null && role == CommonConstants.ROLE_ADMIN;
        boolean isOwner = exist.getCreatorId() != null && exist.getCreatorId().equals(userId);
        boolean isApplyer = exist.getApplyUserId() != null && exist.getApplyUserId().equals(userId);
        if (!isAdmin && !isOwner && !isApplyer) {
            throw new BusinessException(403, "只能操作自己发布/申请的作业");
        }
    }

    private void requireAdmin(Integer role) {
        if (role == null || role != CommonConstants.ROLE_ADMIN) {
            throw new BusinessException(403, "仅管理员可审批");
        }
    }
}
