-- 为已有批注增加单层回复线程。
USE `couple_recycle`;

ALTER TABLE `annotation`
    ADD COLUMN `parent_annotation_id` BIGINT NULL
        COMMENT '回复所属的顶层批注 ID，顶层批注为空' AFTER `author_id`,
    ADD INDEX `idx_annotation_parent` (`parent_annotation_id`, `created_at`);
