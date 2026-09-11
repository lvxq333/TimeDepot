-- 增加私密草稿与“接受 / 提出调整 / 退回沟通”协商流程。
USE `couple_recycle`;

ALTER TABLE `consensus_memo`
    MODIFY COLUMN `status` VARCHAR(30) NOT NULL DEFAULT 'PRIVATE_DRAFT',
    ADD COLUMN `current_revision_id` BIGINT NULL COMMENT '当前生效或私密草稿版本 ID' AFTER `resolution_requested_by`,
    ADD COLUMN `pending_revision_id` BIGINT NULL COMMENT '等待另一方处理的版本 ID' AFTER `current_revision_id`;

ALTER TABLE `consensus_memo_revision`
    ADD COLUMN `action` VARCHAR(20) NOT NULL DEFAULT 'UPDATE' COMMENT '动作：CREATE/UPDATE' AFTER `changed_by`,
    ADD COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'PRIVATE' COMMENT '版本可见及审核状态' AFTER `action`,
    ADD COLUMN `reviewed_by` BIGINT NULL COMMENT '审核人用户 ID' AFTER `status`,
    ADD COLUMN `reviewed_at` DATETIME NULL COMMENT '审核时间' AFTER `reviewed_by`,
    ADD COLUMN `review_note` VARCHAR(500) NULL COMMENT '退回沟通说明' AFTER `reviewed_at`;

ALTER TABLE `memo_commitment`
    MODIFY COLUMN `status` VARCHAR(30) NOT NULL DEFAULT 'PRIVATE_DRAFT';

ALTER TABLE `memo_commitment_revision`
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN `review_note` VARCHAR(500) NULL COMMENT '退回沟通说明' AFTER `reviewed_at`;

UPDATE `consensus_memo` memo
JOIN `consensus_memo_revision` revision
  ON revision.memo_id = memo.id AND revision.version_no = memo.version
SET revision.status = CASE
        WHEN memo.status = 'DRAFT' THEN 'PRIVATE'
        WHEN memo.status = 'PENDING_CONFIRMATION' THEN 'PENDING'
        ELSE 'ACCEPTED'
    END,
    memo.current_revision_id = revision.id,
    memo.pending_revision_id = CASE
        WHEN memo.status = 'PENDING_CONFIRMATION' THEN revision.id
        ELSE NULL
    END;

UPDATE `consensus_memo` SET `status` = 'PRIVATE_DRAFT' WHERE `status` = 'DRAFT';
UPDATE `consensus_memo` SET `status` = 'PENDING_REVIEW' WHERE `status` = 'PENDING_CONFIRMATION';

-- 旧实现中的待确认承诺已经对双方公开，升级后继续保持公开待处理，不能倒退成私密草稿。
UPDATE `memo_commitment` SET `status` = 'PENDING_REVIEW' WHERE `status` = 'PENDING_CONFIRMATION';
