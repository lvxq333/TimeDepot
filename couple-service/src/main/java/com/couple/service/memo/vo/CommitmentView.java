package com.couple.service.memo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/** 当前承诺及其完整版本历史。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommitmentView(Long commitmentId,
                             Long ownerId,
                             String status,
                             String currentContent,
                             Long currentRevisionId,
                             Long pendingRevisionId,
                             Long createdBy,
                             LocalDateTime createdAt,
                             LocalDateTime updatedAt,
                             List<CommitmentRevisionView> revisions) {
}
