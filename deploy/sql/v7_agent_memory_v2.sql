-- v7: agent_chat_message v2（多会话预留 + token 统计）
-- 说明：v6 表仅含测试期数据，直接重建（如已有生产数据请改用 ALTER）
DROP TABLE IF EXISTS agent_chat_message;
CREATE TABLE agent_chat_message (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL COMMENT '用户ID',
    session_id   VARCHAR(64)  NOT NULL DEFAULT 'default' COMMENT '会话ID（未来多会话预留，当前固定 default）',
    role         VARCHAR(16)  NOT NULL COMMENT 'user / assistant',
    content      TEXT         NOT NULL COMMENT '消息内容',
    tokens_used  BIGINT       NULL COMMENT '本轮 LLM 消耗 token（prompt+completion，成本可观测）',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_agent_msg_user (user_id, session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Agent 对话记忆（持久化上下文）';
