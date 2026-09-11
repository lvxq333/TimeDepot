package com.couple.service.memo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/** 线程级共识备忘录、确认状态、承诺与全部留痕。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConsensusMemoView(Long memoId,
                                String threadId,
                                String title,
                                String rootCause,
                                String commonGround,
                                String secretCode,
                                String status,
                                Long participantAId,
                                Long participantBId,
                                Long submittedBy,
                                LocalDateTime participantAConfirmedAt,
                                LocalDateTime participantBConfirmedAt,
                                Long resolutionRequestedBy,
                                Long currentRevisionId,
                                Long pendingRevisionId,
                                Integer version,
                                Long createdBy,
                                LocalDateTime resolvedAt,
                                LocalDateTime createdAt,
                                LocalDateTime updatedAt,
                                List<MemoRevisionView> revisions,
                                List<CommitmentView> commitments) {
}
