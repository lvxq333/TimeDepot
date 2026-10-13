-- 支持事件列表按情绪和日期组合筛选，并加速按事件汇总最近活动。
ALTER TABLE `emotion_letter`
    ADD INDEX `idx_emotion_created_thread` (`emotion_tag`, `created_at`, `thread_id`),
    ADD INDEX `idx_thread_activity` (`thread_id`, `sent_at`, `created_at`);
