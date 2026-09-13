-- v4: 个人课表作息配置（每节课起止时间，每个用户一套）
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行
-- 执行：mysql -u<user> -p schoolhelpdb < v4_schedule_time_config.sql

CREATE TABLE IF NOT EXISTS `schedule_time_config` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT       NOT NULL COMMENT '用户ID',
  `section`    INT          NOT NULL COMMENT '节次 1-12',
  `start_time` VARCHAR(5)   NOT NULL COMMENT '开始时间 HH:mm',
  `end_time`   VARCHAR(5)   NOT NULL COMMENT '结束时间 HH:mm',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_section` (`user_id`, `section`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '个人课表作息配置';
