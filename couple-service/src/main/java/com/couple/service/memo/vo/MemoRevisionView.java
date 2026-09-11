package com.couple.service.memo.vo;

import java.time.LocalDateTime;

/** 可供双方追溯的备忘录正文版本。 */
public record MemoRevisionView(Long revisionId,
                               Integer version,
                               String title,
                               String rootCause,
                               String commonGround,
                               String secretCode,
                               Long changedBy,
                               String action,
                               String status,
                               Long reviewedBy,
                               LocalDateTime reviewedAt,
                               String reviewNote,
                               LocalDateTime createdAt) {
}
