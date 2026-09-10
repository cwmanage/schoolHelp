package com.schoolhelp.biz.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.schoolhelp.biz.entity.AssignmentSubmit;
import com.schoolhelp.biz.feign.CourseFeignClient;
import com.schoolhelp.biz.mapper.AssignmentSubmitMapper;
import com.schoolhelp.common.exception.BusinessException;
import com.schoolhelp.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 作业已提交标记（点"已提交"后不再滚动提醒）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentSubmitService {

    private final AssignmentSubmitMapper submitMapper;
    private final CourseFeignClient courseFeignClient;

    /** 是否已提交 */
    public boolean submitted(Long assignmentId, Long userId) {
        Long count = submitMapper.selectCount(Wrappers.<AssignmentSubmit>lambdaQuery()
                .eq(AssignmentSubmit::getAssignmentId, assignmentId)
                .eq(AssignmentSubmit::getUserId, userId));
        return count != null && count > 0;
    }

    /** 标记已提交（幂等）：先校验作业存在且已通过，避免对不存在/已删作业产生脏数据 */
    public void markSubmitted(Long assignmentId, Long userId) {
        requireAssignmentExists(assignmentId);
        if (submitted(assignmentId, userId)) {
            return;
        }
        AssignmentSubmit s = new AssignmentSubmit();
        s.setAssignmentId(assignmentId);
        s.setUserId(userId);
        s.setSubmitTime(LocalDateTime.now());
        submitMapper.insert(s);
    }

    /** 取消提交标记（幂等） */
    public void unmark(Long assignmentId, Long userId) {
        submitMapper.delete(Wrappers.<AssignmentSubmit>lambdaQuery()
                .eq(AssignmentSubmit::getAssignmentId, assignmentId)
                .eq(AssignmentSubmit::getUserId, userId));
    }

    /** 校验作业存在且已通过（Feign 调 course 服务）；作业已删除时不允许标记 */
    private void requireAssignmentExists(Long assignmentId) {
        try {
            Result<java.util.Map<String, Object>> res = courseFeignClient.assignmentDetail(assignmentId);
            if (res == null || !res.isSuccess() || res.getData() == null) {
                throw new BusinessException(404, "作业不存在或未通过审批");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Feign 调用异常：为不阻塞正常用户，记录日志后放行（避免因下游抖动导致无法标记）
            log.warn("校验作业{}存在性失败，放行标记: {}", assignmentId, e.getMessage());
        }
    }
}
