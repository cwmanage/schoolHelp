-- ============================================================
-- schoolHelp 校园助手 v1 建表脚本
-- 库: schoolhelpdb (已存在) | 字符集: utf8mb4
-- 技术栈: Java17 + SpringBoot3.2 + MySQL8
-- ============================================================
USE schoolhelpdb;

-- 1. 用户表 (0同学 / 1班长 / 2管理员)
CREATE TABLE IF NOT EXISTS `user` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username`    VARCHAR(32)     NOT NULL COMMENT '登录用户名(唯一)',
  `password`    VARCHAR(100)    NOT NULL COMMENT 'BCrypt密码哈希',
  `nickname`    VARCHAR(32)     DEFAULT NULL COMMENT '昵称',
  `role`        TINYINT         NOT NULL DEFAULT 0 COMMENT '角色:0同学 1班长 2管理员',
  `school_id`   VARCHAR(32)     DEFAULT NULL COMMENT '学号(可选绑定)',
  `college`     VARCHAR(64)     DEFAULT NULL COMMENT '院系(选填)',
  `class_name`  VARCHAR(64)     DEFAULT NULL COMMENT '班级(选填)',
  `signature`   VARCHAR(200)    DEFAULT NULL COMMENT '个人签名',
  `avatar_type` TINYINT         NOT NULL DEFAULT 0 COMMENT '头像类型:0系统默认 1自定义上传',
  `avatar_url`  VARCHAR(255)    DEFAULT NULL COMMENT '头像URL',
  `status`      TINYINT         NOT NULL DEFAULT 1 COMMENT '状态:1正常 0禁用',
  `approve_status` TINYINT      NOT NULL DEFAULT 1 COMMENT '班长审批:0待审批 1已通过 2已驳回(同学恒为1)',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_school_id` (`school_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 2. 课程库 (班长/管理员维护)
CREATE TABLE IF NOT EXISTS `course` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '课程ID',
  `name`         VARCHAR(64)     NOT NULL COMMENT '课程名',
  `teacher_name` VARCHAR(32)     DEFAULT NULL COMMENT '教师姓名',
  `teacher_link` VARCHAR(500)    DEFAULT NULL COMMENT '学校师资个人主页链接',
  `class_name`   VARCHAR(64)     DEFAULT NULL COMMENT '适用班级',
  `semester`     VARCHAR(32)     DEFAULT NULL COMMENT '学期,如2026-2027-1',
  `description`  VARCHAR(500)    DEFAULT NULL COMMENT '课程简介',
  `creator_id`   BIGINT UNSIGNED NOT NULL COMMENT '创建人ID(班长/管理员)',
  `status`       TINYINT         NOT NULL DEFAULT 1 COMMENT '状态:1上架 0下架',
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_class` (`class_name`),
  KEY `idx_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程库';

-- 3. 个人课表条目 (学生自录)
CREATE TABLE IF NOT EXISTS `schedule` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
  `course_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '关联课程库ID(可空=纯自录)',
  `course_name`  VARCHAR(64)     NOT NULL COMMENT '课程名(冗余)',
  `week_day`     TINYINT         NOT NULL COMMENT '星期:1-7',
  `start_section` TINYINT        NOT NULL COMMENT '开始节次',
  `end_section`  TINYINT         NOT NULL COMMENT '结束节次',
  `week_type`    TINYINT         NOT NULL DEFAULT 0 COMMENT '周类型:0每周 1单周 2双周',
  `weeks`        VARCHAR(64)     DEFAULT NULL COMMENT '具体周次,如1-16周或1,3,5',
  `room`         VARCHAR(64)     DEFAULT NULL COMMENT '教室',
  `teacher`      VARCHAR(32)     DEFAULT NULL COMMENT '教师(冗余展示)',
  `semester`     VARCHAR(32)     DEFAULT NULL COMMENT '学期',
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人课表条目';

-- 4. 课程资料
CREATE TABLE IF NOT EXISTS `course_material` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `course_id`   BIGINT UNSIGNED NOT NULL COMMENT '所属课程',
  `title`       VARCHAR(128)    NOT NULL COMMENT '资料标题',
  `file_name`   VARCHAR(255)    NOT NULL COMMENT '原始文件名',
  `file_path`   VARCHAR(500)    NOT NULL COMMENT '存储路径(相对URL或NAS路径)',
  `file_size`   BIGINT          DEFAULT 0 COMMENT '字节数',
  `file_type`   VARCHAR(32)     DEFAULT NULL COMMENT '扩展名/类型',
  `uploader_id` BIGINT UNSIGNED NOT NULL COMMENT '上传者',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程资料';

-- 5. 作业
CREATE TABLE IF NOT EXISTS `assignment` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `course_id`   BIGINT UNSIGNED NOT NULL COMMENT '所属课程',
  `title`       VARCHAR(128)    NOT NULL COMMENT '作业标题',
  `content`     TEXT            COMMENT '作业内容/要求',
  `deadline`    DATETIME        NOT NULL COMMENT '截止提交时间',
  `creator_id`  BIGINT UNSIGNED NOT NULL COMMENT '发布人',
  `status`      TINYINT         NOT NULL DEFAULT 1 COMMENT '1有效 0撤销',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_course` (`course_id`),
  KEY `idx_deadline` (`deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作业';

-- 6. 作业提交标记 (用户点"已提交")
CREATE TABLE IF NOT EXISTS `assignment_submit` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `assignment_id` BIGINT UNSIGNED NOT NULL COMMENT '作业ID',
  `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `submit_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '标记时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_assign_user` (`assignment_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作业提交标记';

-- 7. 课程评论 (支持匿名; 管理员可查真实user_id)
CREATE TABLE IF NOT EXISTS `course_comment` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `course_id`    BIGINT UNSIGNED NOT NULL COMMENT '所属课程',
  `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '真实用户ID(匿名也存,供管理员查)',
  `content`      VARCHAR(1000)   NOT NULL COMMENT '评论内容',
  `is_anonymous` TINYINT         NOT NULL DEFAULT 0 COMMENT '0实名 1匿名',
  `status`       TINYINT         NOT NULL DEFAULT 1 COMMENT '1正常 0已删除(逻辑删)',
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_course` (`course_id`, `created_at`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程评论';
