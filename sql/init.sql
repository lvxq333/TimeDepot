-- ============================================================
-- 情侣垃圾回收站 — 数据库初始化脚本
-- Phase 1 · 7 张核心表
-- MySQL 8.x · utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS `couple_recycle`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `couple_recycle`;

-- 1. 用户表
CREATE TABLE `user` (
    `id`                  BIGINT       PRIMARY KEY AUTO_INCREMENT  COMMENT '用户 ID',
    `username`            VARCHAR(50)  NOT NULL UNIQUE              COMMENT '用户名',
    `password`            VARCHAR(255) NOT NULL                     COMMENT '密码（BCrypt 加密）',
    `nickname`            VARCHAR(50)                               COMMENT '昵称，默认取 username',
    `couple_code`         VARCHAR(32)                               COMMENT '6 位配对码，绑定后清空',
    `partner_id`          BIGINT                                    COMMENT '伴侣的用户 ID',
    `partner_remark`      VARCHAR(50)                               COMMENT '我给伴侣设置的备注名',
    `relationship_status` VARCHAR(20)  DEFAULT 'UNBOUND'            COMMENT '关系状态：UNBOUND/BOUND/UNBOUND_HISTORY',
    `membership_plan`     VARCHAR(20)  DEFAULT 'FREE'               COMMENT '会员方案：FREE/PLUS/PRO',
    `membership_expire_at` DATETIME                                  COMMENT '会员到期时间，FREE 为空',
    `bound_at`            DATETIME                                  COMMENT '最近一次绑定时间',
    `unbound_at`          DATETIME                                  COMMENT '最近一次解绑时间',
    `is_deleted`          TINYINT      DEFAULT 0                    COMMENT '软删除标志：0-正常 1-删除',
    `created_at`          DATETIME     DEFAULT CURRENT_TIMESTAMP    COMMENT '创建时间',
    `updated_at`          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE INDEX `uk_user_couple_code` (`couple_code`),
    INDEX `idx_partner_id`        (`partner_id`),
    INDEX `idx_relationship_status` (`relationship_status`),
    INDEX `idx_membership_plan`   (`membership_plan`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 2. 登录会话表（MySQL 为权威数据源，Redis 为访问缓存）
CREATE TABLE `auth_session` (
    `id`              BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '会话记录 ID',
    `session_id`      VARCHAR(64)  NOT NULL                    COMMENT 'JWT sid，对外不暴露数据库主键',
    `user_id`         BIGINT       NOT NULL                    COMMENT '所属用户 ID',
    `username`        VARCHAR(50)  NOT NULL                    COMMENT '签发时用户名，用于校验 JWT 身份',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'   COMMENT '状态：ACTIVE/REVOKED/EXPIRED',
    `device_name`     VARCHAR(128)                             COMMENT '设备名称，后续设备管理使用',
    `client_ip`       VARCHAR(64)                              COMMENT '登录 IP，兼容 IPv6',
    `issued_at`       DATETIME     NOT NULL                    COMMENT '签发时间',
    `expires_at`      DATETIME     NOT NULL                    COMMENT '绝对过期时间',
    `last_active_at`  DATETIME     NOT NULL                    COMMENT '最后活跃时间',
    `revoked_at`      DATETIME                                 COMMENT '撤销时间',
    `revoke_reason`   VARCHAR(64)                              COMMENT '撤销原因',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    `updated_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE INDEX `uk_auth_session_id` (`session_id`),
    INDEX `idx_auth_session_user_status` (`user_id`, `status`),
    INDEX `idx_auth_session_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录会话表';

-- 3. 情侣绑定申请表
CREATE TABLE `couple_bind_request` (
    `id`                  BIGINT      PRIMARY KEY AUTO_INCREMENT COMMENT '申请 ID',
    `requester_id`        BIGINT      NOT NULL                    COMMENT '申请方用户 ID',
    `receiver_id`         BIGINT      NOT NULL                    COMMENT '接收方用户 ID',
    `status`              VARCHAR(20) NOT NULL DEFAULT 'PENDING'  COMMENT '状态：PENDING/ACCEPTED/REJECTED/CANCELLED',
    `active_pair_key`     VARCHAR(64)                             COMMENT '待处理双方唯一键，处理后清空',
    `active_requester_id` BIGINT                                  COMMENT '待处理申请方唯一键，处理后清空',
    `processed_at`        DATETIME                                COMMENT '同意、拒绝或取消时间',
    `created_at`          DATETIME    DEFAULT CURRENT_TIMESTAMP   COMMENT '申请时间',
    `updated_at`          DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE INDEX `uk_bind_request_active_pair` (`active_pair_key`),
    UNIQUE INDEX `uk_bind_request_active_requester` (`active_requester_id`),
    INDEX `idx_bind_request_receiver_status` (`receiver_id`, `status`, `created_at`),
    INDEX `idx_bind_request_requester_status` (`requester_id`, `status`, `created_at`),
    CONSTRAINT `chk_bind_request_not_self` CHECK (`requester_id` <> `receiver_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情侣绑定申请表';

-- 4. 情绪信表（核心业务表）
CREATE TABLE `emotion_letter` (
    `id`              BIGINT       PRIMARY KEY AUTO_INCREMENT  COMMENT '情绪信 ID',
    `sender_id`       BIGINT       NOT NULL                     COMMENT '发送方用户 ID',
    `receiver_id`     BIGINT       NOT NULL                     COMMENT '接收方用户 ID',
    `title`           VARCHAR(100)                               COMMENT '信标题',
    `content`         TEXT         NOT NULL                      COMMENT '信内容（最长 5000 字）',
    `emotion_tag`     VARCHAR(20)  NOT NULL                      COMMENT '情绪标签：angry/sad/doubt/hurt/suggest',
    `intensity`       TINYINT      DEFAULT 5                     COMMENT '情绪强度 1-10，默认 5',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'      COMMENT '状态：DRAFT/COOLDOWN/SENT/READ/RESOLVED',
    `cool_down_end`   DATETIME                                   COMMENT '冷却期结束时间（冗余字段，方便查询）',
    `is_read`         TINYINT      DEFAULT 0                     COMMENT '是否已读：0-未读 1-已读',
    `is_deleted`      TINYINT      DEFAULT 0                     COMMENT '软删除：0-正常 1-删除',
    `sent_at`         DATETIME                                   COMMENT '发送时间',
    `read_at`         DATETIME                                   COMMENT '阅读时间',
    `resolved_at`     DATETIME                                   COMMENT '解决时间',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP     COMMENT '创建时间',
    `updated_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX `idx_sender_id`        (`sender_id`),
    INDEX `idx_receiver_id`      (`receiver_id`),
    INDEX `idx_status`           (`status`),
    INDEX `idx_sender_status`    (`sender_id`, `status`),
    INDEX `idx_receiver_status`  (`receiver_id`, `status`),
    INDEX `idx_created_at`       (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情绪信表';

-- 5. 批注表
CREATE TABLE `annotation` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT  COMMENT '批注 ID',
    `letter_id`     BIGINT       NOT NULL                     COMMENT '所属情绪信 ID',
    `author_id`     BIGINT       NOT NULL                     COMMENT '批注人用户 ID',
    `quote_text`    VARCHAR(500) NOT NULL                     COMMENT '引用的原文片段',
    `quote_start`   INT          NOT NULL                     COMMENT '在 content 中的起始位置（字符偏移）',
    `quote_end`     INT          NOT NULL                     COMMENT '在 content 中的结束位置（字符偏移）',
    `content`       TEXT         NOT NULL                      COMMENT '批注内容',
    `template_type` VARCHAR(20)                               COMMENT '使用的 NVC 模板类型（Phase 2 用）',
    `is_deleted`    TINYINT      DEFAULT 0                    COMMENT '软删除：0-正常 1-删除',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP    COMMENT '创建时间',
    `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX `idx_letter_id` (`letter_id`),
    INDEX `idx_author_id` (`author_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='批注表';

-- 6. 共识备忘录表
CREATE TABLE `consensus_memo` (
    `id`              BIGINT       PRIMARY KEY AUTO_INCREMENT  COMMENT '备忘录 ID',
    `letter_id`       BIGINT       NOT NULL UNIQUE              COMMENT '关联的情绪信 ID（一对一）',
    `root_cause`      TEXT                                      COMMENT '问题根源分析',
    `self_promise`    TEXT                                      COMMENT '自己的承诺',
    `partner_promise` TEXT                                      COMMENT '对方的承诺',
    `secret_code`     VARCHAR(100)                              COMMENT '和解暗号（可含 emoji）',
    `created_by`      BIGINT       NOT NULL                      COMMENT '创建者用户 ID',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP     COMMENT '创建时间',
    `updated_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX `idx_letter_id` (`letter_id`),
    INDEX `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='共识备忘录表';

-- 7. 操作日志表（审计）
CREATE TABLE `letter_log` (
    `id`          BIGINT       PRIMARY KEY AUTO_INCREMENT  COMMENT '日志 ID',
    `letter_id`   BIGINT       NOT NULL                     COMMENT '相关情绪信 ID',
    `operator_id` BIGINT       NOT NULL                     COMMENT '操作人用户 ID',
    `action`      VARCHAR(30)  NOT NULL                     COMMENT '操作类型：CREATE/SUBMIT/SEND/REVERT/READ/RESOLVE/ANNOTATE/DELETE',
    `detail`      VARCHAR(255)                              COMMENT '操作详情或备注',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP    COMMENT '操作时间',

    INDEX `idx_letter_id`   (`letter_id`),
    INDEX `idx_operator_id` (`operator_id`),
    INDEX `idx_action`      (`action`),
    INDEX `idx_created_at`  (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情绪信操作日志表';
