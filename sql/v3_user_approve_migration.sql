# schoolHelp 班长审批 v3 —— 数据库迁移（增量执行脚本，幂等）

-- 说明：在 v1_schema.sql / v2_approval_migration.sql 基础上执行的增量迁移
-- 目标：user 表新增 approve_status 字段，实现「注册为班长 → 管理员审批通过后方可登录」
--   0=待审批(pending)  1=已通过(approved)  2=已驳回(rejected)
-- 同学的 approve_status 恒为 1（无需审批）

-- ============ 1. user 表新增 approve_status ============
ALTER TABLE `user`
  ADD COLUMN `approve_status` TINYINT NOT NULL DEFAULT 1
    COMMENT '班长审批:0待审批 1已通过 2已驳回(同学恒为1)' AFTER `status`;

-- ============ 2. 存量数据回填 ============
-- 既有用户（同学/管理员，以及此前直接建出的班长）视为已通过，避免被挡住登录
UPDATE `user` SET `approve_status` = 1 WHERE `approve_status` IS NULL OR `approve_status` <> 1;
