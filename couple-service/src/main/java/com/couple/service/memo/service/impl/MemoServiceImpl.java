package com.couple.service.memo.service.impl;

import com.couple.common.exception.BadRequestException;
import com.couple.common.exception.BusinessException;
import com.couple.common.exception.ConflictException;
import com.couple.common.exception.ForbiddenException;
import com.couple.common.exception.NotFoundException;
import com.couple.service.letter.entity.EmotionLetterEntity;
import com.couple.service.letter.mapper.EmotionLetterMapper;
import com.couple.service.memo.dto.CommitmentCreateRequest;
import com.couple.service.memo.dto.CommitmentRevisionRequest;
import com.couple.service.memo.dto.MemoCreateRequest;
import com.couple.service.memo.dto.MemoRevisionProposalRequest;
import com.couple.service.memo.dto.MemoUpdateRequest;
import com.couple.service.memo.dto.ReviewReturnRequest;
import com.couple.service.memo.entity.ConsensusMemoEntity;
import com.couple.service.memo.entity.ConsensusMemoRevisionEntity;
import com.couple.service.memo.entity.MemoCommitmentEntity;
import com.couple.service.memo.entity.MemoCommitmentRevisionEntity;
import com.couple.service.memo.mapper.ConsensusMemoMapper;
import com.couple.service.memo.mapper.ConsensusMemoRevisionMapper;
import com.couple.service.memo.mapper.MemoCommitmentMapper;
import com.couple.service.memo.mapper.MemoCommitmentRevisionMapper;
import com.couple.service.memo.service.MemoService;
import com.couple.service.memo.vo.CommitmentRevisionView;
import com.couple.service.memo.vo.CommitmentView;
import com.couple.service.memo.vo.ConsensusMemoView;
import com.couple.service.memo.vo.MemoRevisionView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 私密草稿、双方协商和不可静默篡改承诺的业务实现。 */
@Service
public class MemoServiceImpl implements MemoService {
    private static final String PRIVATE_DRAFT = "PRIVATE_DRAFT";
    private static final String PENDING_REVIEW = "PENDING_REVIEW";
    private static final String ACTIVE = "ACTIVE";
    private static final String CANCELLED = "CANCELLED";
    private static final String RESOLUTION_PENDING = "RESOLUTION_PENDING";
    private static final String RESOLVED = "RESOLVED";
    private static final String PRIVATE = "PRIVATE";
    private static final String PENDING = "PENDING";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String RETURNED = "RETURNED";
    private static final String SUPERSEDED = "SUPERSEDED";
    private static final Set<String> VISIBLE_LETTER_STATUSES = Set.of("SENT", "READ", "RESOLVED");

    private final ConsensusMemoMapper memoMapper;
    private final ConsensusMemoRevisionMapper memoRevisionMapper;
    private final MemoCommitmentMapper commitmentMapper;
    private final MemoCommitmentRevisionMapper commitmentRevisionMapper;
    private final EmotionLetterMapper letterMapper;

    public MemoServiceImpl(ConsensusMemoMapper memoMapper,
                           ConsensusMemoRevisionMapper memoRevisionMapper,
                           MemoCommitmentMapper commitmentMapper,
                           MemoCommitmentRevisionMapper commitmentRevisionMapper,
                           EmotionLetterMapper letterMapper) {
        this.memoMapper = memoMapper;
        this.memoRevisionMapper = memoRevisionMapper;
        this.commitmentMapper = commitmentMapper;
        this.commitmentRevisionMapper = commitmentRevisionMapper;
        this.letterMapper = letterMapper;
    }

    @Override
    @Transactional
    public ConsensusMemoView create(Long userId, MemoCreateRequest request) {
        EmotionLetterEntity anchor = requireVisibleLetter(userId, request.letterId());
        if (!Set.of("READ", "RESOLVED").contains(anchor.getStatus())) {
            throw new ConflictException("至少需要先阅读这封信，才能开始整理共识备忘录");
        }
        if (memoMapper.selectByThreadId(anchor.getThreadId()) != null) {
            throw new ConflictException("这个情绪事件已经有共识备忘录");
        }
        LocalDateTime now = LocalDateTime.now();
        ConsensusMemoEntity memo = new ConsensusMemoEntity();
        memo.setThreadId(anchor.getThreadId());
        applyMemoContent(memo, request.title(), request.rootCause(), request.commonGround(),
                request.secretCode(), anchor.getTitle());
        memo.setStatus(PRIVATE_DRAFT);
        memo.setParticipantAId(Math.min(anchor.getSenderId(), anchor.getReceiverId()));
        memo.setParticipantBId(Math.max(anchor.getSenderId(), anchor.getReceiverId()));
        memo.setVersion(1);
        memo.setCreatedBy(userId);
        memo.setCreatedAt(now);
        memo.setUpdatedAt(now);
        try {
            insertOrFail(memoMapper.insert(memo), "备忘录创建失败，请稍后重试");
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("这个情绪事件已经有共识备忘录");
        }
        ConsensusMemoRevisionEntity revision = saveMemoRevision(memo, userId, "CREATE", PRIVATE, now);
        memo.setCurrentRevisionId(revision.getId());
        updateMemoOrFail(memo);
        return toView(userId, memo);
    }

    @Override
    @Transactional(readOnly = true)
    public ConsensusMemoView getByLetter(Long userId, Long letterId) {
        EmotionLetterEntity anchor = requireVisibleLetter(userId, letterId);
        ConsensusMemoEntity memo = memoMapper.selectByThreadId(anchor.getThreadId());
        if (memo == null) throw new NotFoundException("这个情绪事件还没有共识备忘录");
        requireParticipant(userId, memo);
        requireMemoVisible(userId, memo);
        return toView(userId, memo);
    }

    @Override
    @Transactional
    public ConsensusMemoView update(Long userId, Long memoId, MemoUpdateRequest request) {
        ConsensusMemoEntity memo = requireLockedMemo(userId, memoId, true);
        requireStatus(memo, PRIVATE_DRAFT, "只有自己的私密草稿可以直接编辑");
        requireCreator(userId, memo);
        if (!Objects.equals(request.expectedVersion(), memo.getVersion())) {
            throw new ConflictException("备忘录已更新，请刷新后再编辑");
        }
        applyMemoContent(memo, request.title(), request.rootCause(), request.commonGround(),
                request.secretCode(), null);
        memo.setVersion(memo.getVersion() + 1);
        LocalDateTime now = LocalDateTime.now();
        memo.setUpdatedAt(now);
        ConsensusMemoRevisionEntity revision = saveMemoRevision(memo, userId, "UPDATE", PRIVATE, now);
        memo.setCurrentRevisionId(revision.getId());
        updateMemoOrFail(memo);
        return toView(userId, memo);
    }

    @Override
    @Transactional
    public ConsensusMemoView submit(Long userId, Long memoId) {
        ConsensusMemoEntity memo = requireLockedMemo(userId, memoId, true);
        requireStatus(memo, PRIVATE_DRAFT, "当前备忘录不在可提交的私密草稿状态");
        requireCreator(userId, memo);
        if (isBlank(memo.getRootCause()) && isBlank(memo.getCommonGround())) {
            throw new ConflictException("请至少填写问题归纳或双方共识后再提交");
        }
        ConsensusMemoRevisionEntity revision = requireMemoRevision(memo.getCurrentRevisionId());
        if (!PRIVATE.equals(revision.getStatus()) || !Objects.equals(revision.getChangedBy(), userId)) {
            throw new ConflictException("当前草稿版本异常，请刷新后重试");
        }
        revision.setStatus(PENDING);
        updateMemoRevisionOrFail(revision);
        LocalDateTime now = LocalDateTime.now();
        memo.setStatus(PENDING_REVIEW);
        memo.setPendingRevisionId(revision.getId());
        memo.setSubmittedBy(userId);
        setParticipantConfirmation(memo, userId, now);
        clearOtherParticipantConfirmation(memo, userId);
        memo.setUpdatedAt(now);
        updateMemoOrFail(memo);
        return toView(userId, memo);
    }

    @Override
    @Transactional
    public ConsensusMemoView confirm(Long userId, Long memoId) {
        ConsensusMemoEntity memo = requireLockedMemo(userId, memoId, false);
        if (ACTIVE.equals(memo.getStatus())) return toView(userId, memo);
        requireStatus(memo, PENDING_REVIEW, "当前备忘录没有等待确认的版本");
        ConsensusMemoRevisionEntity revision = requirePendingMemoRevision(memo);
        requireOtherReviewer(userId, revision.getChangedBy(), "不能确认自己提出的备忘录版本");
        LocalDateTime now = LocalDateTime.now();
        revision.setStatus(ACCEPTED);
        revision.setReviewedBy(userId);
        revision.setReviewedAt(now);
        updateMemoRevisionOrFail(revision);
        applyRevision(memo, revision);
        memo.setStatus(ACTIVE);
        memo.setCurrentRevisionId(revision.getId());
        memo.setPendingRevisionId(null);
        setParticipantConfirmation(memo, userId, now);
        memo.setUpdatedAt(now);
        updateMemoOrFail(memo);
        return toView(userId, memo);
    }

    @Override
    @Transactional
    public ConsensusMemoView proposeMemoRevision(Long userId, Long memoId,
                                                  MemoRevisionProposalRequest request) {
        ConsensusMemoEntity memo = requireLockedMemo(userId, memoId, false);
        if (!Set.of(PENDING_REVIEW, ACTIVE).contains(memo.getStatus())) {
            throw new ConflictException("当前备忘录不能提出调整建议");
        }
        if (memo.getPendingRevisionId() != null) {
            ConsensusMemoRevisionEntity previous = requirePendingMemoRevision(memo);
            requireOtherReviewer(userId, previous.getChangedBy(), "请等待对方处理你提出的版本");
            supersedeMemoRevision(previous, userId);
        }
        ConsensusMemoEntity proposal = new ConsensusMemoEntity();
        applyMemoContent(proposal, request.title(), request.rootCause(), request.commonGround(),
                request.secretCode(), null);
        proposal.setId(memo.getId());
        proposal.setVersion(memo.getVersion() + 1);
        LocalDateTime now = LocalDateTime.now();
        ConsensusMemoRevisionEntity revision = saveMemoRevision(proposal, userId, "UPDATE", PENDING, now);
        memo.setVersion(proposal.getVersion());
        memo.setStatus(PENDING_REVIEW);
        memo.setPendingRevisionId(revision.getId());
        memo.setSubmittedBy(userId);
        clearAllConfirmations(memo);
        setParticipantConfirmation(memo, userId, now);
        memo.setUpdatedAt(now);
        updateMemoOrFail(memo);
        return toView(userId, memo);
    }

    @Override
    @Transactional
    public ConsensusMemoView returnMemo(Long userId, Long memoId, ReviewReturnRequest request) {
        ConsensusMemoEntity memo = requireLockedMemo(userId, memoId, false);
        requireStatus(memo, PENDING_REVIEW, "当前备忘录没有等待处理的版本");
        ConsensusMemoRevisionEntity revision = requirePendingMemoRevision(memo);
        requireOtherReviewer(userId, revision.getChangedBy(), "不能退回自己提出的备忘录版本");
        LocalDateTime now = LocalDateTime.now();
        revision.setStatus(RETURNED);
        revision.setReviewedBy(userId);
        revision.setReviewedAt(now);
        revision.setReviewNote(normalize(request.reason(), 500, null));
        updateMemoRevisionOrFail(revision);
        memo.setPendingRevisionId(null);
        memo.setStatus(hasAcceptedMemoRevision(memo) ? ACTIVE : PRIVATE_DRAFT);
        clearAllConfirmations(memo);
        memo.setUpdatedAt(now);
        updateMemoOrFail(memo);
        return toView(userId, memo);
    }

    @Override
    @Transactional
    public ConsensusMemoView requestResolution(Long userId, Long memoId) {
        ConsensusMemoEntity memo = requireLockedMemo(userId, memoId, false);
        if (RESOLUTION_PENDING.equals(memo.getStatus())
                && Objects.equals(userId, memo.getResolutionRequestedBy())) return toView(userId, memo);
        requireStatus(memo, ACTIVE, "只有双方已确认的备忘录可以申请结束事件");
        if (commitmentMapper.selectByMemoId(memoId).stream()
                .anyMatch(item -> item.getPendingRevisionId() != null)) {
            throw new ConflictException("还有承诺等待确认，请先处理后再结束事件");
        }
        memo.setStatus(RESOLUTION_PENDING);
        memo.setResolutionRequestedBy(userId);
        memo.setUpdatedAt(LocalDateTime.now());
        updateMemoOrFail(memo);
        return toView(userId, memo);
    }

    @Override
    @Transactional
    public ConsensusMemoView confirmResolution(Long userId, Long memoId) {
        ConsensusMemoEntity memo = requireLockedMemo(userId, memoId, false);
        if (RESOLVED.equals(memo.getStatus())) return toView(userId, memo);
        requireStatus(memo, RESOLUTION_PENDING, "当前事件没有等待确认的解决申请");
        requireOtherReviewer(userId, memo.getResolutionRequestedBy(), "解决申请需要由另一方确认");
        LocalDateTime now = LocalDateTime.now();
        memo.setStatus(RESOLVED);
        memo.setResolvedAt(now);
        memo.setUpdatedAt(now);
        updateMemoOrFail(memo);
        return toView(userId, memo);
    }

    @Override
    @Transactional
    public ConsensusMemoView rejectResolution(Long userId, Long memoId) {
        ConsensusMemoEntity memo = requireLockedMemo(userId, memoId, false);
        requireStatus(memo, RESOLUTION_PENDING, "当前事件没有等待处理的解决申请");
        requireOtherReviewer(userId, memo.getResolutionRequestedBy(), "解决申请需要由另一方处理");
        memo.setStatus(ACTIVE);
        memo.setResolutionRequestedBy(null);
        memo.setUpdatedAt(LocalDateTime.now());
        updateMemoOrFail(memo);
        return toView(userId, memo);
    }

    @Override
    @Transactional
    public CommitmentView createCommitment(Long userId, Long memoId, CommitmentCreateRequest request) {
        ConsensusMemoEntity memo = requireLockedMemo(userId, memoId, true);
        requireCommitmentContainerEditable(memo);
        LocalDateTime now = LocalDateTime.now();
        MemoCommitmentEntity commitment = new MemoCommitmentEntity();
        commitment.setMemoId(memoId);
        commitment.setOwnerId(userId);
        commitment.setStatus(PRIVATE_DRAFT);
        commitment.setCreatedBy(userId);
        commitment.setCreatedAt(now);
        commitment.setUpdatedAt(now);
        insertOrFail(commitmentMapper.insert(commitment), "承诺草稿创建失败，请稍后重试");
        MemoCommitmentRevisionEntity revision = newCommitmentRevision(
                commitment.getId(), 1, "CREATE", request.content().trim(), userId, PRIVATE, now);
        insertOrFail(commitmentRevisionMapper.insert(revision), "承诺草稿保存失败，请稍后重试");
        commitment.setCurrentRevisionId(revision.getId());
        updateCommitmentOrFail(commitment);
        return toCommitmentView(userId, commitment);
    }

    @Override
    @Transactional
    public CommitmentView updateCommitmentDraft(Long userId, Long commitmentId,
                                                 CommitmentRevisionRequest request) {
        MemoCommitmentEntity commitment = requireLockedCommitment(userId, commitmentId, true);
        requireOwner(userId, commitment);
        requireStatus(commitment, PRIVATE_DRAFT, "只有自己的私密承诺草稿可以直接编辑");
        int nextVersion = nextCommitmentVersion(commitmentId);
        MemoCommitmentRevisionEntity revision = newCommitmentRevision(commitmentId, nextVersion,
                "UPDATE", request.content().trim(), userId, PRIVATE, LocalDateTime.now());
        insertOrFail(commitmentRevisionMapper.insert(revision), "承诺草稿保存失败，请稍后重试");
        commitment.setCurrentRevisionId(revision.getId());
        commitment.setUpdatedAt(revision.getCreatedAt());
        updateCommitmentOrFail(commitment);
        return toCommitmentView(userId, commitment);
    }

    @Override
    @Transactional
    public void deleteCommitmentDraft(Long userId, Long commitmentId) {
        MemoCommitmentEntity commitment = requireLockedCommitment(userId, commitmentId, true);
        requireOwner(userId, commitment);
        requireStatus(commitment, PRIVATE_DRAFT, "已经提交过的承诺不能删除，只能保留记录并协商取消");
        if (hasPublicCommitmentRevision(commitmentId)) {
            throw new ConflictException("已经提交过的承诺不能删除，只能保留记录并协商处理");
        }
        commitmentRevisionMapper.deletePrivateByCommitmentId(commitmentId);
        if (commitmentMapper.deleteById(commitmentId) != 1) {
            throw new ConflictException("承诺草稿状态已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional
    public CommitmentView submitCommitment(Long userId, Long commitmentId) {
        MemoCommitmentEntity commitment = requireLockedCommitment(userId, commitmentId, true);
        requireOwner(userId, commitment);
        requireStatus(commitment, PRIVATE_DRAFT, "当前承诺不在可提交的私密草稿状态");
        MemoCommitmentRevisionEntity revision = requireCommitmentRevision(commitment.getCurrentRevisionId());
        if (!PRIVATE.equals(revision.getStatus())) throw new ConflictException("当前承诺草稿版本异常");
        revision.setStatus(PENDING);
        updateCommitmentRevisionOrFail(revision);
        commitment.setStatus(PENDING_REVIEW);
        commitment.setPendingRevisionId(revision.getId());
        commitment.setUpdatedAt(LocalDateTime.now());
        updateCommitmentOrFail(commitment);
        return toCommitmentView(userId, commitment);
    }

    @Override
    @Transactional
    public CommitmentView proposeCommitmentRevision(Long userId, Long commitmentId,
                                                     CommitmentRevisionRequest request) {
        MemoCommitmentEntity commitment = requireLockedCommitment(userId, commitmentId, false);
        if (!Set.of(PENDING_REVIEW, ACTIVE).contains(commitment.getStatus())) {
            throw new ConflictException("当前承诺不能提出调整建议");
        }
        if (commitment.getPendingRevisionId() != null) {
            MemoCommitmentRevisionEntity previous = requireCommitmentRevision(commitment.getPendingRevisionId());
            requireOtherReviewer(userId, previous.getProposedBy(), "请等待对方处理你提出的版本");
            supersedeCommitmentRevision(previous, userId);
        }
        MemoCommitmentRevisionEntity revision = newCommitmentRevision(commitmentId,
                nextCommitmentVersion(commitmentId), "UPDATE", request.content().trim(), userId,
                PENDING, LocalDateTime.now());
        insertOrFail(commitmentRevisionMapper.insert(revision), "承诺调整建议保存失败，请稍后重试");
        commitment.setStatus(PENDING_REVIEW);
        commitment.setPendingRevisionId(revision.getId());
        commitment.setUpdatedAt(revision.getCreatedAt());
        updateCommitmentOrFail(commitment);
        return toCommitmentView(userId, commitment);
    }

    @Override
    @Transactional
    public CommitmentView requestCommitmentCancellation(Long userId, Long commitmentId) {
        MemoCommitmentEntity commitment = requireLockedCommitment(userId, commitmentId, false);
        requireOwner(userId, commitment);
        requireStatus(commitment, ACTIVE, "只有生效中的本人承诺可以申请取消");
        MemoCommitmentRevisionEntity revision = newCommitmentRevision(commitmentId,
                nextCommitmentVersion(commitmentId), "CANCEL", currentCommitmentContent(commitment),
                userId, PENDING, LocalDateTime.now());
        insertOrFail(commitmentRevisionMapper.insert(revision), "取消申请保存失败，请稍后重试");
        commitment.setStatus(PENDING_REVIEW);
        commitment.setPendingRevisionId(revision.getId());
        commitment.setUpdatedAt(revision.getCreatedAt());
        updateCommitmentOrFail(commitment);
        return toCommitmentView(userId, commitment);
    }

    @Override
    @Transactional
    public CommitmentView reviewCommitmentRevision(Long userId, Long revisionId, boolean accepted) {
        if (!accepted) {
            return returnCommitmentRevision(userId, revisionId, new ReviewReturnRequest(null));
        }
        ReviewContext context = lockReviewContext(userId, revisionId);
        requireOtherReviewer(userId, context.revision().getProposedBy(), "不能确认自己提出的承诺版本");
        LocalDateTime now = LocalDateTime.now();
        context.revision().setStatus(ACCEPTED);
        context.revision().setReviewedBy(userId);
        context.revision().setReviewedAt(now);
        updateCommitmentRevisionOrFail(context.revision());
        context.commitment().setPendingRevisionId(null);
        if ("CANCEL".equals(context.revision().getAction())) {
            context.commitment().setStatus(CANCELLED);
        } else {
            context.commitment().setStatus(ACTIVE);
            context.commitment().setCurrentRevisionId(revisionId);
        }
        context.commitment().setUpdatedAt(now);
        updateCommitmentOrFail(context.commitment());
        return toCommitmentView(userId, context.commitment());
    }

    @Override
    @Transactional
    public CommitmentView returnCommitmentRevision(Long userId, Long revisionId,
                                                    ReviewReturnRequest request) {
        ReviewContext context = lockReviewContext(userId, revisionId);
        requireOtherReviewer(userId, context.revision().getProposedBy(), "不能退回自己提出的承诺版本");
        LocalDateTime now = LocalDateTime.now();
        context.revision().setStatus(RETURNED);
        context.revision().setReviewedBy(userId);
        context.revision().setReviewedAt(now);
        context.revision().setReviewNote(normalize(request.reason(), 500, null));
        updateCommitmentRevisionOrFail(context.revision());
        context.commitment().setPendingRevisionId(null);
        context.commitment().setStatus(hasAcceptedCommitmentRevision(context.commitment())
                ? ACTIVE : PRIVATE_DRAFT);
        context.commitment().setUpdatedAt(now);
        updateCommitmentOrFail(context.commitment());
        return toCommitmentView(userId, context.commitment());
    }

    private ReviewContext lockReviewContext(Long userId, Long revisionId) {
        MemoCommitmentRevisionEntity snapshotRevision = commitmentRevisionMapper.selectById(revisionId);
        if (snapshotRevision == null) throw new NotFoundException("承诺版本不存在");
        MemoCommitmentEntity snapshotCommitment = commitmentMapper.selectById(snapshotRevision.getCommitmentId());
        if (snapshotCommitment == null) throw new NotFoundException("承诺不存在");
        requireLockedMemo(userId, snapshotCommitment.getMemoId(), false);
        MemoCommitmentEntity commitment = commitmentMapper.selectByIdForUpdate(snapshotCommitment.getId());
        MemoCommitmentRevisionEntity revision = commitmentRevisionMapper.selectByIdForUpdate(revisionId);
        if (commitment == null || revision == null
                || !Objects.equals(commitment.getPendingRevisionId(), revisionId)
                || !PENDING.equals(revision.getStatus())) {
            throw new ConflictException("这条承诺版本已经不是当前待处理版本");
        }
        return new ReviewContext(commitment, revision);
    }

    private ConsensusMemoEntity requireLockedMemo(Long userId, Long memoId, boolean allowPrivateOwner) {
        ConsensusMemoEntity memo = memoMapper.selectByIdForUpdate(memoId);
        if (memo == null) throw new NotFoundException("共识备忘录不存在");
        requireParticipant(userId, memo);
        if (!allowPrivateOwner || !Objects.equals(userId, memo.getCreatedBy())) requireMemoVisible(userId, memo);
        return memo;
    }

    private MemoCommitmentEntity requireLockedCommitment(Long userId, Long commitmentId,
                                                          boolean allowPrivateOwner) {
        MemoCommitmentEntity snapshot = commitmentMapper.selectById(commitmentId);
        if (snapshot == null) throw new NotFoundException("承诺不存在");
        requireLockedMemo(userId, snapshot.getMemoId(), allowPrivateOwner);
        MemoCommitmentEntity locked = commitmentMapper.selectByIdForUpdate(commitmentId);
        if (locked == null) throw new ConflictException("承诺状态已变化，请刷新后重试");
        if (PRIVATE_DRAFT.equals(locked.getStatus()) && !Objects.equals(userId, locked.getOwnerId())
                && !hasPublicCommitmentRevision(commitmentId)) {
            throw new NotFoundException("承诺不存在");
        }
        return locked;
    }

    private void requireMemoVisible(Long userId, ConsensusMemoEntity memo) {
        if (PRIVATE_DRAFT.equals(memo.getStatus()) && !Objects.equals(userId, memo.getCreatedBy())
                && publicMemoRevisions(memo.getId()).isEmpty()) {
            throw new NotFoundException("这个情绪事件还没有共识备忘录");
        }
    }

    private ConsensusMemoView toView(Long userId, ConsensusMemoEntity memo) {
        List<ConsensusMemoRevisionEntity> all = memoRevisionMapper.selectByMemoId(memo.getId());
        List<ConsensusMemoRevisionEntity> visible = all.stream()
                .filter(item -> !PRIVATE.equals(item.getStatus()) || Objects.equals(userId, item.getChangedBy()))
                .toList();
        ConsensusMemoRevisionEntity displayed = null;
        String displayedStatus = memo.getStatus();
        Long displayedCurrentId = memo.getCurrentRevisionId();
        if (PRIVATE_DRAFT.equals(memo.getStatus()) && !Objects.equals(userId, memo.getCreatedBy())) {
            displayed = visible.stream().filter(item -> !PRIVATE.equals(item.getStatus()))
                    .reduce((first, second) -> second).orElse(null);
            displayedStatus = RETURNED;
            displayedCurrentId = displayed == null ? null : displayed.getId();
        }
        String title = displayed == null ? memo.getTitle() : displayed.getTitle();
        String rootCause = displayed == null ? memo.getRootCause() : displayed.getRootCause();
        String commonGround = displayed == null ? memo.getCommonGround() : displayed.getCommonGround();
        String secretCode = displayed == null ? memo.getSecretCode() : displayed.getSecretCode();
        List<MemoRevisionView> revisions = visible.stream().map(this::toMemoRevisionView).toList();
        List<CommitmentView> commitments = commitmentMapper.selectByMemoId(memo.getId()).stream()
                .filter(item -> isCommitmentVisible(userId, item))
                .map(item -> toCommitmentView(userId, item)).toList();
        return new ConsensusMemoView(memo.getId(), memo.getThreadId(), title, rootCause, commonGround,
                secretCode, displayedStatus, memo.getParticipantAId(), memo.getParticipantBId(),
                memo.getSubmittedBy(), memo.getParticipantAConfirmedAt(), memo.getParticipantBConfirmedAt(),
                memo.getResolutionRequestedBy(), displayedCurrentId, memo.getPendingRevisionId(),
                memo.getVersion(), memo.getCreatedBy(), memo.getResolvedAt(), memo.getCreatedAt(),
                memo.getUpdatedAt(), revisions, commitments);
    }

    private CommitmentView toCommitmentView(Long userId, MemoCommitmentEntity commitment) {
        List<MemoCommitmentRevisionEntity> all = commitmentRevisionMapper.selectByCommitmentId(commitment.getId());
        List<MemoCommitmentRevisionEntity> visible = all.stream()
                .filter(item -> !PRIVATE.equals(item.getStatus()) || Objects.equals(userId, item.getProposedBy()))
                .toList();
        String status = commitment.getStatus();
        Long currentId = commitment.getCurrentRevisionId();
        if (PRIVATE_DRAFT.equals(status) && !Objects.equals(userId, commitment.getOwnerId())) {
            MemoCommitmentRevisionEntity publicVersion = visible.stream()
                    .filter(item -> !PRIVATE.equals(item.getStatus())).reduce((a, b) -> b).orElse(null);
            status = RETURNED;
            currentId = publicVersion == null ? null : publicVersion.getId();
        }
        Long finalCurrentId = currentId;
        String currentContent = visible.stream().filter(item -> Objects.equals(item.getId(), finalCurrentId))
                .map(MemoCommitmentRevisionEntity::getContent).findFirst().orElse(null);
        List<CommitmentRevisionView> revisions = visible.stream().map(this::toCommitmentRevisionView).toList();
        return new CommitmentView(commitment.getId(), commitment.getOwnerId(), status, currentContent,
                currentId, commitment.getPendingRevisionId(), commitment.getCreatedBy(),
                commitment.getCreatedAt(), commitment.getUpdatedAt(), revisions);
    }

    private MemoRevisionView toMemoRevisionView(ConsensusMemoRevisionEntity item) {
        return new MemoRevisionView(item.getId(), item.getVersionNo(), item.getTitle(), item.getRootCause(),
                item.getCommonGround(), item.getSecretCode(), item.getChangedBy(), item.getAction(),
                item.getStatus(), item.getReviewedBy(), item.getReviewedAt(), item.getReviewNote(),
                item.getCreatedAt());
    }

    private CommitmentRevisionView toCommitmentRevisionView(MemoCommitmentRevisionEntity item) {
        return new CommitmentRevisionView(item.getId(), item.getVersionNo(), item.getAction(),
                item.getContent(), item.getProposedBy(), item.getStatus(), item.getReviewedBy(),
                item.getReviewedAt(), item.getReviewNote(), item.getCreatedAt());
    }

    private ConsensusMemoRevisionEntity saveMemoRevision(ConsensusMemoEntity source, Long userId,
                                                          String action, String status, LocalDateTime now) {
        ConsensusMemoRevisionEntity revision = new ConsensusMemoRevisionEntity();
        revision.setMemoId(source.getId());
        revision.setVersionNo(source.getVersion());
        revision.setTitle(source.getTitle());
        revision.setRootCause(source.getRootCause());
        revision.setCommonGround(source.getCommonGround());
        revision.setSecretCode(source.getSecretCode());
        revision.setChangedBy(userId);
        revision.setAction(action);
        revision.setStatus(status);
        revision.setCreatedAt(now);
        insertOrFail(memoRevisionMapper.insert(revision), "备忘录版本保存失败，请稍后重试");
        return revision;
    }

    private MemoCommitmentRevisionEntity newCommitmentRevision(Long commitmentId, int version,
                                                                String action, String content, Long userId,
                                                                String status, LocalDateTime now) {
        MemoCommitmentRevisionEntity revision = new MemoCommitmentRevisionEntity();
        revision.setCommitmentId(commitmentId);
        revision.setVersionNo(version);
        revision.setAction(action);
        revision.setContent(content);
        revision.setProposedBy(userId);
        revision.setStatus(status);
        revision.setCreatedAt(now);
        return revision;
    }

    private ConsensusMemoRevisionEntity requirePendingMemoRevision(ConsensusMemoEntity memo) {
        if (memo.getPendingRevisionId() == null) throw new ConflictException("待处理版本不存在");
        ConsensusMemoRevisionEntity revision = requireMemoRevision(memo.getPendingRevisionId());
        if (!PENDING.equals(revision.getStatus())) throw new ConflictException("待处理版本状态已变化");
        return revision;
    }

    private ConsensusMemoRevisionEntity requireMemoRevision(Long revisionId) {
        ConsensusMemoRevisionEntity revision = memoRevisionMapper.selectById(revisionId);
        if (revision == null) throw new ConflictException("备忘录版本不存在");
        return revision;
    }

    private MemoCommitmentRevisionEntity requireCommitmentRevision(Long revisionId) {
        MemoCommitmentRevisionEntity revision = commitmentRevisionMapper.selectById(revisionId);
        if (revision == null) throw new ConflictException("承诺版本不存在");
        return revision;
    }

    private void supersedeMemoRevision(ConsensusMemoRevisionEntity revision, Long reviewer) {
        revision.setStatus(SUPERSEDED);
        revision.setReviewedBy(reviewer);
        revision.setReviewedAt(LocalDateTime.now());
        updateMemoRevisionOrFail(revision);
    }

    private void supersedeCommitmentRevision(MemoCommitmentRevisionEntity revision, Long reviewer) {
        revision.setStatus(SUPERSEDED);
        revision.setReviewedBy(reviewer);
        revision.setReviewedAt(LocalDateTime.now());
        updateCommitmentRevisionOrFail(revision);
    }

    private boolean hasAcceptedMemoRevision(ConsensusMemoEntity memo) {
        return memoRevisionMapper.selectByMemoId(memo.getId()).stream()
                .anyMatch(item -> ACCEPTED.equals(item.getStatus()));
    }

    private boolean hasAcceptedCommitmentRevision(MemoCommitmentEntity commitment) {
        return commitmentRevisionMapper.selectByCommitmentId(commitment.getId()).stream()
                .anyMatch(item -> ACCEPTED.equals(item.getStatus()) && !"CANCEL".equals(item.getAction()));
    }

    private boolean hasPublicCommitmentRevision(Long commitmentId) {
        return commitmentRevisionMapper.selectByCommitmentId(commitmentId).stream()
                .anyMatch(item -> !PRIVATE.equals(item.getStatus()));
    }

    private boolean isCommitmentVisible(Long userId, MemoCommitmentEntity commitment) {
        return Objects.equals(userId, commitment.getOwnerId()) || hasPublicCommitmentRevision(commitment.getId());
    }

    private List<ConsensusMemoRevisionEntity> publicMemoRevisions(Long memoId) {
        return memoRevisionMapper.selectByMemoId(memoId).stream()
                .filter(item -> !PRIVATE.equals(item.getStatus())).toList();
    }

    private int nextCommitmentVersion(Long commitmentId) {
        return commitmentRevisionMapper.selectByCommitmentId(commitmentId).stream()
                .mapToInt(MemoCommitmentRevisionEntity::getVersionNo).max().orElse(0) + 1;
    }

    private String currentCommitmentContent(MemoCommitmentEntity commitment) {
        return commitmentRevisionMapper.selectByCommitmentId(commitment.getId()).stream()
                .filter(item -> Objects.equals(item.getId(), commitment.getCurrentRevisionId()))
                .map(MemoCommitmentRevisionEntity::getContent).findFirst()
                .orElseThrow(() -> new ConflictException("承诺当前版本缺失"));
    }

    private void applyRevision(ConsensusMemoEntity memo, ConsensusMemoRevisionEntity revision) {
        memo.setTitle(revision.getTitle());
        memo.setRootCause(revision.getRootCause());
        memo.setCommonGround(revision.getCommonGround());
        memo.setSecretCode(revision.getSecretCode());
    }

    private void applyMemoContent(ConsensusMemoEntity memo, String title, String rootCause,
                                  String commonGround, String secretCode, String titleFallback) {
        memo.setTitle(normalize(title, 100, titleFallback));
        memo.setRootCause(normalize(rootCause, 5000, null));
        memo.setCommonGround(normalize(commonGround, 5000, null));
        memo.setSecretCode(normalize(secretCode, 100, null));
    }

    private EmotionLetterEntity requireVisibleLetter(Long userId, Long letterId) {
        EmotionLetterEntity letter = letterMapper.selectById(letterId);
        if (letter == null || Integer.valueOf(1).equals(letter.getIsDeleted())) {
            throw new NotFoundException("情绪信不存在");
        }
        boolean sender = Objects.equals(userId, letter.getSenderId());
        boolean receiver = Objects.equals(userId, letter.getReceiverId());
        if (!sender && !receiver) throw new ForbiddenException("无权查看这个情绪事件");
        if (receiver && !VISIBLE_LETTER_STATUSES.contains(letter.getStatus())) {
            throw new ForbiddenException("这封情绪信尚未发送给你");
        }
        return letter;
    }

    private void requireParticipant(Long userId, ConsensusMemoEntity memo) {
        if (!Objects.equals(userId, memo.getParticipantAId())
                && !Objects.equals(userId, memo.getParticipantBId())) {
            throw new ForbiddenException("无权访问这份共识备忘录");
        }
    }

    private void requireCreator(Long userId, ConsensusMemoEntity memo) {
        if (!Objects.equals(userId, memo.getCreatedBy())) throw new ForbiddenException("只有草稿创建者可以编辑或提交");
    }

    private void requireOwner(Long userId, MemoCommitmentEntity commitment) {
        if (!Objects.equals(userId, commitment.getOwnerId())) throw new ForbiddenException("只能编辑或提交自己的承诺");
    }

    private void requireOtherReviewer(Long userId, Long proposerId, String message) {
        if (Objects.equals(userId, proposerId)) throw new ForbiddenException(message);
    }

    private void requireCommitmentContainerEditable(ConsensusMemoEntity memo) {
        if (Set.of(RESOLUTION_PENDING, RESOLVED).contains(memo.getStatus())) {
            throw new ConflictException("事件结束申请期间或结束后不能新增承诺");
        }
    }

    private void requireStatus(ConsensusMemoEntity memo, String expected, String message) {
        if (!expected.equals(memo.getStatus())) throw new ConflictException(message);
    }

    private void requireStatus(MemoCommitmentEntity commitment, String expected, String message) {
        if (!expected.equals(commitment.getStatus())) throw new ConflictException(message);
    }

    private void setParticipantConfirmation(ConsensusMemoEntity memo, Long userId, LocalDateTime now) {
        if (Objects.equals(userId, memo.getParticipantAId())) memo.setParticipantAConfirmedAt(now);
        else memo.setParticipantBConfirmedAt(now);
    }

    private void clearOtherParticipantConfirmation(ConsensusMemoEntity memo, Long userId) {
        if (Objects.equals(userId, memo.getParticipantAId())) memo.setParticipantBConfirmedAt(null);
        else memo.setParticipantAConfirmedAt(null);
    }

    private void clearAllConfirmations(ConsensusMemoEntity memo) {
        memo.setParticipantAConfirmedAt(null);
        memo.setParticipantBConfirmedAt(null);
    }

    private String normalize(String value, int maxLength, String fallback) {
        if (value == null || value.isBlank()) return fallback == null || fallback.isBlank() ? null : fallback.trim();
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new BadRequestException("文本长度超过限制");
        return normalized;
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }

    private void updateMemoOrFail(ConsensusMemoEntity memo) {
        if (memoMapper.updateById(memo) != 1) throw new ConflictException("备忘录状态已变化，请刷新后重试");
    }

    private void updateMemoRevisionOrFail(ConsensusMemoRevisionEntity revision) {
        if (memoRevisionMapper.updateById(revision) != 1) throw new ConflictException("备忘录版本状态已变化");
    }

    private void updateCommitmentOrFail(MemoCommitmentEntity commitment) {
        if (commitmentMapper.updateById(commitment) != 1) throw new ConflictException("承诺状态已变化，请刷新后重试");
    }

    private void updateCommitmentRevisionOrFail(MemoCommitmentRevisionEntity revision) {
        if (commitmentRevisionMapper.updateById(revision) != 1) throw new ConflictException("承诺版本状态已变化");
    }

    private void insertOrFail(int rows, String message) {
        if (rows != 1) throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    private record ReviewContext(MemoCommitmentEntity commitment,
                                 MemoCommitmentRevisionEntity revision) { }
}
