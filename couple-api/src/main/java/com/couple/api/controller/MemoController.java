package com.couple.api.controller;

import com.couple.common.response.ApiResponse;
import com.couple.common.security.UserContext;
import com.couple.service.memo.dto.CommitmentCreateRequest;
import com.couple.service.memo.dto.CommitmentRevisionRequest;
import com.couple.service.memo.dto.MemoCreateRequest;
import com.couple.service.memo.dto.MemoRevisionProposalRequest;
import com.couple.service.memo.dto.MemoUpdateRequest;
import com.couple.service.memo.dto.ReviewReturnRequest;
import com.couple.service.memo.service.MemoService;
import com.couple.service.memo.vo.CommitmentView;
import com.couple.service.memo.vo.ConsensusMemoView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 线程级共识备忘录、双向确认和承诺版本接口。 */
@RestController
@RequestMapping("/api/memo")
public class MemoController {
    private final MemoService memoService;

    public MemoController(MemoService memoService) {
        this.memoService = memoService;
    }

    /** 为整条情绪信回复链创建一份备忘录草稿。 */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ConsensusMemoView>> create(
            @Valid @RequestBody MemoCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                HttpStatus.CREATED, memoService.create(UserContext.requireUserId(), request)));
    }

    /** 仅创建者可编辑未提交的私密草稿。 */
    @PutMapping("/{memoId}")
    public ApiResponse<ConsensusMemoView> update(
            @PathVariable Long memoId, @Valid @RequestBody MemoUpdateRequest request) {
        return ApiResponse.success(memoService.update(UserContext.requireUserId(), memoId, request));
    }

    /** 提交人先确认，等待另一方确认。 */
    @PostMapping("/{memoId}/submit")
    public ApiResponse<ConsensusMemoView> submit(@PathVariable Long memoId) {
        return ApiResponse.success(memoService.submit(UserContext.requireUserId(), memoId));
    }

    /** 另一方确认后，备忘录正式生效。 */
    @PostMapping("/{memoId}/confirm")
    public ApiResponse<ConsensusMemoView> confirm(@PathVariable Long memoId) {
        return ApiResponse.success(memoService.confirm(UserContext.requireUserId(), memoId));
    }

    /** 对待确认或已生效备忘录提出完整调整版本，不直接覆盖原内容。 */
    @PostMapping("/{memoId}/revisions")
    public ResponseEntity<ApiResponse<ConsensusMemoView>> proposeMemoRevision(
            @PathVariable Long memoId, @Valid @RequestBody MemoRevisionProposalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                HttpStatus.CREATED, memoService.proposeMemoRevision(
                        UserContext.requireUserId(), memoId, request)));
    }

    /** 暂不认同当前版本并退回继续沟通。 */
    @PostMapping("/{memoId}/return")
    public ApiResponse<ConsensusMemoView> returnMemo(
            @PathVariable Long memoId, @Valid @RequestBody ReviewReturnRequest request) {
        return ApiResponse.success(memoService.returnMemo(UserContext.requireUserId(), memoId, request));
    }

    /** 一方申请将整个情绪事件标记为已解决。 */
    @PostMapping("/{memoId}/resolution/request")
    public ApiResponse<ConsensusMemoView> requestResolution(@PathVariable Long memoId) {
        return ApiResponse.success(memoService.requestResolution(UserContext.requireUserId(), memoId));
    }

    /** 另一方确认后才结束整个事件。 */
    @PostMapping("/{memoId}/resolution/confirm")
    public ApiResponse<ConsensusMemoView> confirmResolution(@PathVariable Long memoId) {
        return ApiResponse.success(memoService.confirmResolution(UserContext.requireUserId(), memoId));
    }

    /** 另一方不同意结束时恢复为生效状态。 */
    @PostMapping("/{memoId}/resolution/reject")
    public ApiResponse<ConsensusMemoView> rejectResolution(@PathVariable Long memoId) {
        return ApiResponse.success(memoService.rejectResolution(UserContext.requireUserId(), memoId));
    }

    /** 创建仅本人可见的承诺草稿，承诺人由登录身份确定。 */
    @PostMapping("/{memoId}/commitments")
    public ResponseEntity<ApiResponse<CommitmentView>> createCommitment(
            @PathVariable Long memoId, @Valid @RequestBody CommitmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                HttpStatus.CREATED,
                memoService.createCommitment(UserContext.requireUserId(), memoId, request)));
    }

    /** 本人编辑尚未提交的私密承诺草稿。 */
    @PutMapping("/commitments/{commitmentId}")
    public ApiResponse<CommitmentView> updateCommitmentDraft(
            @PathVariable Long commitmentId,
            @Valid @RequestBody CommitmentRevisionRequest request) {
        return ApiResponse.success(memoService.updateCommitmentDraft(
                UserContext.requireUserId(), commitmentId, request));
    }

    /** 只有从未提交的私密草稿可以真正删除。 */
    @DeleteMapping("/commitments/{commitmentId}")
    public ResponseEntity<Void> deleteCommitmentDraft(@PathVariable Long commitmentId) {
        memoService.deleteCommitmentDraft(UserContext.requireUserId(), commitmentId);
        return ResponseEntity.noContent().build();
    }

    /** 本人提交承诺后，对方才可以看见并处理。 */
    @PostMapping("/commitments/{commitmentId}/submit")
    public ApiResponse<CommitmentView> submitCommitment(@PathVariable Long commitmentId) {
        return ApiResponse.success(memoService.submitCommitment(
                UserContext.requireUserId(), commitmentId));
    }

    /** 提出承诺修改，原版本在对方确认前继续生效。 */
    @PostMapping("/commitments/{commitmentId}/revisions")
    public ResponseEntity<ApiResponse<CommitmentView>> proposeCommitmentRevision(
            @PathVariable Long commitmentId,
            @Valid @RequestBody CommitmentRevisionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                HttpStatus.CREATED, memoService.proposeCommitmentRevision(
                        UserContext.requireUserId(), commitmentId, request)));
    }

    /** 申请取消承诺，不会立即删除或失效。 */
    @PostMapping("/commitments/{commitmentId}/cancellation")
    public ResponseEntity<ApiResponse<CommitmentView>> requestCommitmentCancellation(
            @PathVariable Long commitmentId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                HttpStatus.CREATED, memoService.requestCommitmentCancellation(
                        UserContext.requireUserId(), commitmentId)));
    }

    /** 由提议者之外的另一方接受待确认版本。 */
    @PostMapping("/commitment-revisions/{revisionId}/confirm")
    public ApiResponse<CommitmentView> confirmCommitmentRevision(@PathVariable Long revisionId) {
        return ApiResponse.success(memoService.reviewCommitmentRevision(
                UserContext.requireUserId(), revisionId, true));
    }

    /** 暂不认同并退回继续沟通，可附带温和说明。 */
    @PostMapping("/commitment-revisions/{revisionId}/return")
    public ApiResponse<CommitmentView> returnCommitmentRevision(
            @PathVariable Long revisionId, @Valid @RequestBody ReviewReturnRequest request) {
        return ApiResponse.success(memoService.returnCommitmentRevision(
                UserContext.requireUserId(), revisionId, request));
    }
}
