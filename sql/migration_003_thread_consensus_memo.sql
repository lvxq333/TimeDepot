-- 将旧的一信一备忘录升级为一话题一备忘录，并增加不可静默篡改的承诺版本记录。
USE `couple_recycle`;

ALTER TABLE `consensus_memo`
    ADD COLUMN `thread_id` CHAR(36) NULL COMMENT '关联的话题线程 ID' AFTER `id`,
    ADD COLUMN `title` VARCHAR(100) NULL COMMENT '事件标题' AFTER `thread_id`,
    ADD COLUMN `common_ground` TEXT NULL COMMENT '双方达成的共识' AFTER `root_cause`,
    ADD COLUMN `status` VARCHAR(30) NOT NULL DEFAULT 'DRAFT' COMMENT '备忘录状态（由 migration_004 升级为私密草稿）' AFTER `secret_code`,
    ADD COLUMN `participant_a_id` BIGINT NULL COMMENT '参与方 A' AFTER `status`,
    ADD COLUMN `participant_b_id` BIGINT NULL COMMENT '参与方 B' AFTER `participant_a_id`,
    ADD COLUMN `submitted_by` BIGINT NULL COMMENT '最近提交确认的用户 ID' AFTER `participant_b_id`,
    ADD COLUMN `participant_a_confirmed_at` DATETIME NULL COMMENT '参与方 A 确认时间' AFTER `submitted_by`,
    ADD COLUMN `participant_b_confirmed_at` DATETIME NULL COMMENT '参与方 B 确认时间' AFTER `participant_a_confirmed_at`,
    ADD COLUMN `resolution_requested_by` BIGINT NULL COMMENT '申请结束事件的用户 ID' AFTER `participant_b_confirmed_at`,
    ADD COLUMN `version` INT NOT NULL DEFAULT 1 COMMENT '当前版本号' AFTER `resolution_requested_by`,
    ADD COLUMN `resolved_at` DATETIME NULL COMMENT '双方确认解决时间' AFTER `created_by`;

UPDATE `consensus_memo` memo
JOIN `emotion_letter` letter ON letter.id = memo.letter_id
SET memo.thread_id = letter.thread_id,
    memo.title = letter.title,
    memo.participant_a_id = LEAST(letter.sender_id, letter.receiver_id),
    memo.participant_b_id = GREATEST(letter.sender_id, letter.receiver_id);

CREATE TABLE `consensus_memo_revision` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `memo_id` BIGINT NOT NULL,
    `version_no` INT NOT NULL,
    `title` VARCHAR(100),
    `root_cause` TEXT,
    `common_ground` TEXT,
    `secret_code` VARCHAR(100),
    `changed_by` BIGINT NOT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX `uk_memo_revision_version` (`memo_id`, `version_no`),
    INDEX `idx_memo_revision_changed_by` (`changed_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `memo_commitment` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `memo_id` BIGINT NOT NULL,
    `owner_id` BIGINT NOT NULL,
    `status` VARCHAR(30) NOT NULL DEFAULT 'PENDING_CONFIRMATION',
    `current_revision_id` BIGINT,
    `pending_revision_id` BIGINT,
    `created_by` BIGINT NOT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_commitment_memo` (`memo_id`, `created_at`),
    INDEX `idx_commitment_owner` (`owner_id`),
    INDEX `idx_commitment_pending_revision` (`pending_revision_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `memo_commitment_revision` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `commitment_id` BIGINT NOT NULL,
    `version_no` INT NOT NULL,
    `action` VARCHAR(20) NOT NULL,
    `content` TEXT,
    `proposed_by` BIGINT NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `reviewed_by` BIGINT,
    `reviewed_at` DATETIME,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX `uk_commitment_revision_version` (`commitment_id`, `version_no`),
    INDEX `idx_commitment_revision_status` (`commitment_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `consensus_memo_revision`
    (`memo_id`, `version_no`, `title`, `root_cause`, `common_ground`, `secret_code`, `changed_by`, `created_at`)
SELECT `id`, 1, `title`, `root_cause`, `common_ground`, `secret_code`, `created_by`, `created_at`
FROM `consensus_memo`;

-- 旧承诺转为已接受版本，保留原数据；新业务中的修改和取消都必须再次经另一方确认。
INSERT INTO `memo_commitment` (`memo_id`, `owner_id`, `status`, `created_by`, `created_at`, `updated_at`)
SELECT `id`, `created_by`, 'ACTIVE', `created_by`, `created_at`, `updated_at`
FROM `consensus_memo` WHERE `self_promise` IS NOT NULL AND TRIM(`self_promise`) <> '';

INSERT INTO `memo_commitment_revision`
    (`commitment_id`, `version_no`, `action`, `content`, `proposed_by`, `status`, `reviewed_by`, `reviewed_at`, `created_at`)
SELECT commitment.id, 1, 'CREATE', memo.self_promise, memo.created_by, 'ACCEPTED',
       CASE WHEN memo.created_by = memo.participant_a_id THEN memo.participant_b_id ELSE memo.participant_a_id END,
       memo.created_at, memo.created_at
FROM `memo_commitment` commitment JOIN `consensus_memo` memo ON memo.id = commitment.memo_id
WHERE commitment.owner_id = memo.created_by;

UPDATE `memo_commitment` commitment
JOIN `memo_commitment_revision` revision ON revision.commitment_id = commitment.id AND revision.version_no = 1
SET commitment.current_revision_id = revision.id;

INSERT INTO `memo_commitment` (`memo_id`, `owner_id`, `status`, `created_by`, `created_at`, `updated_at`)
SELECT `id`, CASE WHEN `created_by` = `participant_a_id` THEN `participant_b_id` ELSE `participant_a_id` END,
       'ACTIVE', `created_by`, `created_at`, `updated_at`
FROM `consensus_memo` WHERE `partner_promise` IS NOT NULL AND TRIM(`partner_promise`) <> '';

INSERT INTO `memo_commitment_revision`
    (`commitment_id`, `version_no`, `action`, `content`, `proposed_by`, `status`, `reviewed_by`, `reviewed_at`, `created_at`)
SELECT commitment.id, 1, 'CREATE', memo.partner_promise, memo.created_by, 'ACCEPTED',
       commitment.owner_id, memo.created_at, memo.created_at
FROM `memo_commitment` commitment JOIN `consensus_memo` memo ON memo.id = commitment.memo_id
WHERE commitment.owner_id <> memo.created_by;

UPDATE `memo_commitment` commitment
JOIN `memo_commitment_revision` revision ON revision.commitment_id = commitment.id AND revision.version_no = 1
SET commitment.current_revision_id = revision.id
WHERE commitment.current_revision_id IS NULL;

ALTER TABLE `consensus_memo`
    MODIFY COLUMN `thread_id` CHAR(36) NOT NULL,
    MODIFY COLUMN `participant_a_id` BIGINT NOT NULL,
    MODIFY COLUMN `participant_b_id` BIGINT NOT NULL,
    ADD UNIQUE INDEX `uk_consensus_memo_thread` (`thread_id`),
    ADD INDEX `idx_memo_participants` (`participant_a_id`, `participant_b_id`),
    DROP INDEX `letter_id`,
    DROP COLUMN `letter_id`,
    DROP COLUMN `self_promise`,
    DROP COLUMN `partner_promise`;
