package com.schoolhelp.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 审批请求 DTO（管理员审批课程/作业/资料）
 */
@Data
public class ReviewDTO {

    /** 通过=STATUS_APPROVED / 驳回=STATUS_REJECTED */
    @Min(value = 1, message = "审批动作不合法")
    @Max(value = 2, message = "审批动作不合法")
    private Integer action;

    /** 审批意见：驳回时必填原因；通过时可选填 */
    private String note;
}
