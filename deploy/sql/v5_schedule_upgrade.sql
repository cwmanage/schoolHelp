-- v5: 课表升级（学期设置 / 校园日历 / 调课记录 / 课程库教室字段）
-- 全部幂等，可重复执行
-- 执行：mysql -u<user> -p schoolhelpdb < v5_schedule_upgrade.sql

-- 1. 课程库加常用教室列（幂等：先查后加）
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course' AND COLUMN_NAME = 'room');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `course` ADD COLUMN `room` VARCHAR(64) NULL COMMENT ''常用教室'' AFTER `description`',
  'SELECT ''course.room already exists''');
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- 2. 学期设置（第一周开始日期，每用户每学期一份）
CREATE TABLE IF NOT EXISTS `schedule_semester_setting` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT      NOT NULL,
  `semester`   VARCHAR(32) NOT NULL,
  `week1_date` DATE        NOT NULL COMMENT '第一周周一日期',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_sem` (`user_id`, `semester`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学期设置（第一周开始日期）';

-- 3. 校园日历事件（考试/竞赛/节假日/活动）
CREATE TABLE IF NOT EXISTS `campus_calendar_event` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `event_type`   VARCHAR(16)  NOT NULL COMMENT 'exam考试/contest竞赛/holiday节假日/activity活动',
  `title`        VARCHAR(128) NOT NULL,
  `event_date`   DATE         NULL COMMENT '单日事件日期',
  `date_start`   DATE         NULL COMMENT '区间开始',
  `date_end`     DATE         NULL COMMENT '区间结束',
  `time_note`    VARCHAR(64)  NULL COMMENT '模糊时间说明（如 上午/全天/以官方为准）',
  `detail`       VARCHAR(512) NULL,
  `source_url`   VARCHAR(256) NULL,
  `data_version` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '内容版本（仅内容变化时更新）',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event` (`event_type`, `title`, `event_date`),
  KEY `idx_date` (`event_date`),
  KEY `idx_range` (`date_start`, `date_end`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '校园日历事件';

-- 4. 用户日历已读时间（登录后"有更新"提示用）
CREATE TABLE IF NOT EXISTS `user_calendar_read` (
  `user_id`      BIGINT   NOT NULL,
  `last_read_at` DATETIME NOT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户日历已读记录';

-- 5. 调课记录（临时调课=仅本周生效）
CREATE TABLE IF NOT EXISTS `schedule_change` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT NOT NULL,
  `schedule_id`   BIGINT NOT NULL COMMENT '原课表条目',
  `week_start`    DATE   NOT NULL COMMENT '生效周（周一起始，临时调课=本周）',
  `week_day`      INT    NOT NULL COMMENT '调至星期 1-7',
  `start_section` INT    NOT NULL,
  `end_section`   INT    NOT NULL,
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_week` (`user_id`, `week_start`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '课表调课记录';
