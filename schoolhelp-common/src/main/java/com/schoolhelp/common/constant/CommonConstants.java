package com.schoolhelp.common.constant;

/**
 * 全局常量
 */
public interface CommonConstants {

    /** 角色：同学 */
    int ROLE_STUDENT = 0;
    /** 角色：班长 */
    int ROLE_MONITOR = 1;
    /** 角色：管理员 */
    int ROLE_ADMIN = 2;

    /** JWT 请求头 */
    String TOKEN_HEADER = "Authorization";
    /** JWT 前缀 */
    String TOKEN_PREFIX = "Bearer ";

    /** 评论匿名 */
    int COMMENT_ANON = 1;
    /** 评论实名 */
    int COMMENT_REAL = 0;

    /** 作业临期提醒阈值(天) */
    int ASSIGNMENT_URGENT_DAYS = 5;

    /** 默认管理员账号（首次启动自动创建，请及时改密） */
    String DEFAULT_ADMIN_USERNAME = "admin";
    String DEFAULT_ADMIN_PASSWORD = "admin123";

    /** 审批状态：待审批 */
    int STATUS_PENDING = 0;
    /** 审批状态：已通过 */
    int STATUS_APPROVED = 1;
    /** 审批状态：已驳回 */
    int STATUS_REJECTED = 2;
    /** 课程状态：下架 */
    int STATUS_OFFLINE = 3;

    /** 申请类型：课程 */
    int APPLY_TYPE_COURSE = 1;
    /** 申请类型：作业 */
    int APPLY_TYPE_ASSIGNMENT = 2;
    /** 申请类型：资料 */
    int APPLY_TYPE_MATERIAL = 3;
}
