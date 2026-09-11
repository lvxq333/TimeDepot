package com.couple.service.memo.vo;

import java.time.LocalDateTime;

/** 承诺的提出、修改、取消及审核结果。 */
public record CommitmentRevisionView(Long revisionId,
                                     Integer version,
                                     String action,
                                     String content,
                                     Long proposedBy,
                                     String status,
                                     Long reviewedBy,
                                     LocalDateTime reviewedAt,
                                     String reviewNote,
                                     LocalDateTime createdAt) {
}
