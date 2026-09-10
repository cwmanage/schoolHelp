# schoolHelp 审批流改造 v2 —— 数据库迁移（增量执行脚本，幂等）

-- 说明：在 v1_schema.sql 基础上执行的增量迁移
-- 目标：course / assignment / course_material 三表引入审批状态机
--   0=待审批(pending)  1=已通过(approved)  2=已驳回(rejected)
-- 课程额外保留 3=下架(offline) 语义（管理员手动下架）

-- ============ 1. course 表 ============
ALTER TABLE `course`
  ADD COLUMN `apply_user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '申请人ID' AFTER `creator_id`,
  ADD COLUMN `apply_note`    VARCHAR(500)  DEFAULT NULL COMMENT '申请说明' AFTER `apply_user_id`,
  ADD COLUMN `review_note`   VARCHAR(500)  DEFAULT NULL COMMENT '审批意见(驳回原因)' AFTER `apply_note`,
  ADD COLUMN `reviewed_at`   DATETIME      DEFAULT NULL COMMENT '审批时间' AFTER `review_note`,
  MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0待审批 1已通过 2已驳回 3下架';

-- ============ 2. assignment 表 ============
ALTER TABLE `assignment`
  ADD COLUMN `apply_user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '申请人ID' AFTER `creator_id`,
  ADD COLUMN `apply_note`    VARCHAR(500)  DEFAULT NULL COMMENT '申请说明' AFTER `apply_user_id`,
  ADD COLUMN `review_note`   VARCHAR(500)  DEFAULT NULL COMMENT '审批意见' AFTER `apply_note`,
  ADD COLUMN `reviewed_at`   DATETIME      DEFAULT NULL COMMENT '审批时间' AFTER `review_note`,
  MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0待审批 1已通过 2已驳回';

-- ============ 3. course_material 表 ============
ALTER TABLE `course_material`
  ADD COLUMN `status`        TINYINT       NOT NULL DEFAULT 0 COMMENT '0待审批 1已通过 2已驳回' AFTER `uploader_id`,
  ADD COLUMN `apply_user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '申请人ID' AFTER `status`,
  ADD COLUMN `apply_note`    VARCHAR(500)  DEFAULT NULL COMMENT '申请说明' AFTER `apply_user_id`,
  ADD COLUMN `review_note`   VARCHAR(500)  DEFAULT NULL COMMENT '审批意见' AFTER `apply_note`,
  ADD COLUMN `reviewed_at`   DATETIME      DEFAULT NULL COMMENT '审批时间' AFTER `review_note`;

-- ============ 存量数据回填 ============
-- 老数据（管理员/班长直接创建）视为已通过
UPDATE `course`          SET `status` = 1, `apply_user_id` = `creator_id` WHERE `status` IN (0,1);
UPDATE `assignment`      SET `status` = 1, `apply_user_id` = `creator_id` WHERE `status` IN (0,1);
UPDATE `course_material` SET `status` = 1, `apply_user_id` = `uploader_id`;
