package com.schoolhelp.biz.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.schoolhelp.common.constant.CommonConstants;
import com.schoolhelp.common.exception.BusinessException;
import com.schoolhelp.common.result.Result;
import com.schoolhelp.biz.entity.CourseComment;
import com.schoolhelp.biz.feign.CourseFeignClient;
import com.schoolhelp.biz.mapper.CourseCommentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 课程评论区服务
 * 匿名评论：对外显示"匿名同学+黑客头像"；管理员可查真实 user_id、可删
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseCommentService {

    private final CourseCommentMapper commentMapper;
    private final CourseFeignClient courseFeignClient;

    /** 评论列表（对用户屏蔽匿名者真实ID，管理员可见） */
    public List<CourseComment> listByCourse(Long courseId, boolean isAdmin) {
        List<CourseComment> list = commentMapper.selectList(Wrappers.<CourseComment>lambdaQuery()
                .eq(CourseComment::getCourseId, courseId)
                .eq(CourseComment::getStatus, 1)
                .orderByAsc(CourseComment::getCreatedAt));
        if (!isAdmin) {
            // 非管理员看不到匿名者真实 userId（字段置 null）
            for (CourseComment c : list) {
                if (c.getIsAnonymous() != null && c.getIsAnonymous() == 1) {
                    c.setUserId(null);
                }
            }
        }
        return list;
    }

    /** 发表评论（实名/匿名）：校验课程存在/已通过，避免对不存在课程产生脏评论 */
    public Long add(Long courseId, Long userId, String content, Integer isAnonymous) {
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(400, "评论内容不能为空");
        }
        if (content.length() > 500) {
            throw new BusinessException(400, "评论最多500字");
        }
        // 评论仅对已通过课程开放；课程不存在/未通过时拒绝
        try {
            Result<java.util.Map<String, Object>> res = courseFeignClient.courseDetail(courseId);
            if (res == null || !res.isSuccess() || res.getData() == null) {
                throw new BusinessException(404, "课程不存在或未通过审批");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 下游抖动时不阻塞评论（记录日志放行）
            log.warn("校验课程{}存在性失败，放行评论: {}", courseId, e.getMessage());
        }
        CourseComment c = new CourseComment();
        c.setCourseId(courseId);
        c.setUserId(userId);
        c.setContent(content.trim());
        c.setIsAnonymous(isAnonymous != null && isAnonymous == 1 ? 1 : 0);
        c.setStatus(1);
        commentMapper.insert(c);
        return c.getId();
    }

    /** 删除评论：管理员可删任意；普通用户可删自己的 */
    public void delete(Long commentId, Long userId, Integer role) {
        CourseComment c = commentMapper.selectById(commentId);
        if (c == null) {
            return;
        }
        boolean isAdmin = role != null && role == CommonConstants.ROLE_ADMIN;
        boolean isOwner = c.getUserId() != null && c.getUserId().equals(userId);
        if (!isAdmin && !isOwner) {
            throw new BusinessException(403, "无权删除该评论");
        }
        c.setStatus(0); // 逻辑删除
        commentMapper.updateById(c);
    }

    /** 管理员查某评论的真实身份 */
    public CourseComment adminDetail(Long commentId) {
        CourseComment c = commentMapper.selectById(commentId);
        if (c == null) {
            throw new BusinessException(404, "评论不存在");
        }
        return c;
    }
}
