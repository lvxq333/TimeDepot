-- 将信件回复链提升为可分页、可筛选的事件，并回填既有数据。
CREATE TABLE IF NOT EXISTS `letter_thread` (
    `thread_id`        CHAR(36)     PRIMARY KEY,
    `title`            VARCHAR(100),
    `participant_a_id` BIGINT       NOT NULL,
    `participant_b_id` BIGINT       NOT NULL,
    `status`           VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RESOLVED/REOPENED/CLOSED',
    `resolved_by`      BIGINT,
    `resolved_at`      DATETIME,
    `reopened_at`      DATETIME,
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_letter_thread_participant_a` (`participant_a_id`, `updated_at`),
    INDEX `idx_letter_thread_participant_b` (`participant_b_id`, `updated_at`),
    INDEX `idx_letter_thread_status` (`status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情绪信事件线程';

INSERT INTO `letter_thread` (`thread_id`, `title`, `participant_a_id`, `participant_b_id`,
                             `status`, `resolved_at`, `reopened_at`, `created_at`, `updated_at`)
SELECT first_letter.thread_id,
       first_letter.title,
       LEAST(first_letter.sender_id, first_letter.receiver_id),
       GREATEST(first_letter.sender_id, first_letter.receiver_id),
       CASE
           WHEN aggregate.latest_resolved_at IS NULL THEN 'OPEN'
           WHEN aggregate.latest_resolved_at >= aggregate.latest_activity_at THEN 'RESOLVED'
           ELSE 'REOPENED'
       END,
       aggregate.latest_resolved_at,
       CASE WHEN aggregate.latest_resolved_at IS NOT NULL
                  AND aggregate.latest_resolved_at < aggregate.latest_activity_at
            THEN aggregate.latest_activity_at END,
       aggregate.started_at,
       aggregate.latest_activity_at
FROM emotion_letter first_letter
JOIN (
    SELECT thread_id,
           MIN(id) AS first_letter_id,
           MIN(created_at) AS started_at,
           MAX(COALESCE(sent_at, created_at)) AS latest_activity_at,
           MAX(resolved_at) AS latest_resolved_at
    FROM emotion_letter
    WHERE is_deleted = 0
    GROUP BY thread_id
) aggregate ON aggregate.first_letter_id = first_letter.id
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    participant_a_id = VALUES(participant_a_id),
    participant_b_id = VALUES(participant_b_id),
    status = VALUES(status),
    resolved_at = VALUES(resolved_at),
    reopened_at = VALUES(reopened_at),
    updated_at = VALUES(updated_at);
