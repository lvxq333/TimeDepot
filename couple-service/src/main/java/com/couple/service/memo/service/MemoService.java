package com.couple.service.memo.service;

import com.couple.service.memo.dto.CommitmentCreateRequest;
import com.couple.service.memo.dto.CommitmentRevisionRequest;
import com.couple.service.memo.dto.MemoCreateRequest;
import com.couple.service.memo.dto.MemoRevisionProposalRequest;
import com.couple.service.memo.dto.MemoUpdateRequest;
import com.couple.service.memo.dto.ReviewReturnRequest;
import com.couple.service.memo.vo.CommitmentView;
import com.couple.service.memo.vo.ConsensusMemoView;

/** 线程级共识备忘录及双向确认承诺业务。 */
public interface MemoService {
    ConsensusMemoView create(Long userId, MemoCreateRequest request);
    ConsensusMemoView getByLetter(Long userId, Long letterId);
    ConsensusMemoView update(Long userId, Long memoId, MemoUpdateRequest request);
    ConsensusMemoView submit(Long userId, Long memoId);
    ConsensusMemoView confirm(Long userId, Long memoId);
    ConsensusMemoView proposeMemoRevision(Long userId, Long memoId, MemoRevisionProposalRequest request);
    ConsensusMemoView returnMemo(Long userId, Long memoId, ReviewReturnRequest request);
    ConsensusMemoView requestResolution(Long userId, Long memoId);
    ConsensusMemoView confirmResolution(Long userId, Long memoId);
    ConsensusMemoView rejectResolution(Long userId, Long memoId);
    CommitmentView createCommitment(Long userId, Long memoId, CommitmentCreateRequest request);
    CommitmentView updateCommitmentDraft(Long userId, Long commitmentId, CommitmentRevisionRequest request);
    void deleteCommitmentDraft(Long userId, Long commitmentId);
    CommitmentView submitCommitment(Long userId, Long commitmentId);
    CommitmentView proposeCommitmentRevision(Long userId, Long commitmentId,
                                              CommitmentRevisionRequest request);
    CommitmentView requestCommitmentCancellation(Long userId, Long commitmentId);
    CommitmentView reviewCommitmentRevision(Long userId, Long revisionId, boolean accepted);
    CommitmentView returnCommitmentRevision(Long userId, Long revisionId, ReviewReturnRequest request);
}
