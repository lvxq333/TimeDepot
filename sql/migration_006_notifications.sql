CREATE TABLE IF NOT EXISTS `user_notification` (
    `id`             BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '通知 ID',
    `event_id`       CHAR(36)     NOT NULL COMMENT '实时事件唯一 ID，用于幂等',
    `type`           VARCHAR(50)  NOT NULL COMMENT '业务事件类型',
    `recipient_id`   BIGINT       NOT NULL COMMENT '接收用户 ID',
    `actor_id`       BIGINT       NOT NULL COMMENT '触发用户 ID',
    `aggregate_type` VARCHAR(30)  NOT NULL COMMENT '关联资源类型',
    `aggregate_id`   VARCHAR(64)  NOT NULL COMMENT '关联资源 ID',
    `payload_json`   JSON         NOT NULL COMMENT '非敏感展示参数',
    `read_at`        DATETIME     NULL COMMENT '首次已读时间',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    UNIQUE INDEX `uk_notification_event` (`event_id`),
    INDEX `idx_notification_recipient` (`recipient_id`, `created_at`, `id`),
    INDEX `idx_notification_unread` (`recipient_id`, `read_at`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户站内通知';
