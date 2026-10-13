CREATE TABLE IF NOT EXISTS `user_recovery_question` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `position` TINYINT NOT NULL,
    `question` VARCHAR(100) NOT NULL,
    `answer_hash` VARCHAR(255) NOT NULL,
    UNIQUE INDEX `uk_recovery_user_position` (`user_id`, `position`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
