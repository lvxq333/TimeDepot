CREATE TABLE IF NOT EXISTS `letter_visibility` (
    `letter_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `hidden_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`letter_id`, `user_id`),
    INDEX `idx_letter_visibility_user` (`user_id`, `letter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
