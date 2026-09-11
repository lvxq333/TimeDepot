-- 情绪信话题关联迁移。
-- 适用于已经执行过旧版 init.sql 的数据库；请在停机窗口执行。

ALTER TABLE emotion_letter
    ADD COLUMN thread_id CHAR(36) NULL COMMENT '同一话题 ID' AFTER receiver_id,
    ADD COLUMN reply_to_letter_id BIGINT NULL COMMENT '直接回复的上一封信 ID' AFTER thread_id;

UPDATE emotion_letter
SET thread_id = UUID()
WHERE thread_id IS NULL;

ALTER TABLE emotion_letter
    MODIFY COLUMN thread_id CHAR(36) NOT NULL,
    ADD INDEX idx_thread_created_at (thread_id, created_at),
    ADD INDEX idx_reply_to_letter_id (reply_to_letter_id);
