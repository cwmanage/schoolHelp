-- v6: Agent 对话记忆持久化（按消息行存——大赛"数据库挂载/多轮记忆"同款思路）
-- 幂等可重复执行
CREATE TABLE IF NOT EXISTS agent_chat_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL COMMENT '用户ID',
    role        VARCHAR(16)  NOT NULL COMMENT 'user / assistant',
    content     TEXT         NOT NULL COMMENT '消息内容',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_agent_msg_user (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Agent 对话记忆（持久化上下文）';
