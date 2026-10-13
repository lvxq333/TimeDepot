package com.couple.service.memo.service.impl;

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
import com.couple.service.user.entity.UserEntity;
import com.couple.service.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoServiceImplTest {
    private MemoServiceImpl service;
    private ConsensusMemoEntity memo;
    private final List<ConsensusMemoRevisionEntity> memoRevisions = new ArrayList<>();
    private final List<MemoCommitmentEntity> commitments = new ArrayList<>();
    private final List<MemoCommitmentRevisionEntity> commitmentRevisions = new ArrayList<>();
    private EmotionLetterEntity letter;
    private UserEntity alice;
    private UserEntity bob;
    private long memoRevisionSequence;
    private long commitmentSequence;
    private long commitmentRevisionSequence;

    @BeforeEach
    void setUp() {
        memo = null;
        memoRevisions.clear();
        commitments.clear();
        commitmentRevisions.clear();
        memoRevisionSequence = 10;
        commitmentSequence = 20;
        commitmentRevisionSequence = 30;
        letter = letter();
        alice = new UserEntity();
        alice.setId(1L);
        alice.setPartnerId(2L);
        alice.setRelationshipStatus("BOUND");
        bob = new UserEntity();
        bob.setId(2L);
        bob.setPartnerId(1L);
        bob.setRelationshipStatus("BOUND");

        ConsensusMemoMapper memoMapper = proxy(ConsensusMemoMapper.class, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "insert" -> {
                    memo = (ConsensusMemoEntity) args[0];
                    memo.setId(5L);
                    yield 1;
                }
                case "selectByThreadId" -> memo != null && memo.getThreadId().equals(args[0]) ? memo : null;
                case "selectByIdForUpdate", "selectById" -> memo != null && memo.getId().equals(args[0]) ? memo : null;
                case "updateById" -> 1;
                default -> defaultValue(method.getReturnType());
            };
        });
        ConsensusMemoRevisionMapper memoRevisionMapper = proxy(
                ConsensusMemoRevisionMapper.class, (proxy, method, args) -> switch (method.getName()) {
                    case "insert" -> {
                        ConsensusMemoRevisionEntity revision = (ConsensusMemoRevisionEntity) args[0];
                        revision.setId(memoRevisionSequence++);
                        memoRevisions.add(revision);
                        yield 1;
                    }
                    case "updateById" -> 1;
                    case "selectByMemoId" -> memoRevisions.stream()
                            .filter(item -> item.getMemoId().equals(args[0])).toList();
                    case "selectById" -> memoRevisions.stream()
                            .filter(item -> item.getId().equals(args[0])).findFirst().orElse(null);
                    default -> defaultValue(method.getReturnType());
                });
        MemoCommitmentMapper commitmentMapper = proxy(MemoCommitmentMapper.class, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "insert" -> {
                    MemoCommitmentEntity item = (MemoCommitmentEntity) args[0];
                    item.setId(commitmentSequence++);
                    commitments.add(item);
                    yield 1;
                }
                case "updateById" -> 1;
                case "deleteById" -> commitments.removeIf(item -> item.getId().equals(args[0])) ? 1 : 0;
                case "selectByMemoId" -> commitments.stream()
                        .filter(item -> item.getMemoId().equals(args[0])).toList();
                case "selectByIdForUpdate", "selectById" -> commitments.stream()
                        .filter(item -> item.getId().equals(args[0])).findFirst().orElse(null);
                default -> defaultValue(method.getReturnType());
            };
        });
        MemoCommitmentRevisionMapper commitmentRevisionMapper = proxy(
                MemoCommitmentRevisionMapper.class, (proxy, method, args) -> switch (method.getName()) {
                    case "insert" -> {
                        MemoCommitmentRevisionEntity revision = (MemoCommitmentRevisionEntity) args[0];
                        revision.setId(commitmentRevisionSequence++);
                        commitmentRevisions.add(revision);
                        yield 1;
                    }
                    case "updateById" -> 1;
                    case "deletePrivateByCommitmentId" -> {
                        int before = commitmentRevisions.size();
                        commitmentRevisions.removeIf(item -> item.getCommitmentId().equals(args[0]));
                        yield before - commitmentRevisions.size();
                    }
                    case "selectByCommitmentId" -> commitmentRevisions.stream()
                            .filter(item -> item.getCommitmentId().equals(args[0])).toList();
                    case "selectByIdForUpdate", "selectById" -> commitmentRevisions.stream()
                            .filter(item -> item.getId().equals(args[0])).findFirst().orElse(null);
                    default -> defaultValue(method.getReturnType());
                });
        EmotionLetterMapper letterMapper = proxy(EmotionLetterMapper.class, (proxy, method, args) ->
                "countVisibleInThread".equals(method.getName()) ? 1 :
                ("selectRootByThreadId".equals(method.getName()) && letter.getThreadId().equals(args[0])
                        || "selectById".equals(method.getName()) && letter.getId().equals(args[0]))
                        ? letter : defaultValue(method.getReturnType()));
        UserMapper userMapper = proxy(UserMapper.class, (proxy, method, args) -> {
            if ("selectPairForUpdate".equals(method.getName())) return List.of(alice, bob);
            return defaultValue(method.getReturnType());
        });
        service = new MemoServiceImpl(memoMapper, memoRevisionMapper, commitmentMapper,
                commitmentRevisionMapper, letterMapper,
                proxy(com.couple.service.letter.mapper.LetterVisibilityMapper.class,
                        (proxy, method, args) -> defaultValue(method.getReturnType())), userMapper);
    }

    @Test
    void memoIsPrivateUntilOwnerSubmitsIt() {
        service.create(1L, new MemoCreateRequest(1L, "事件", "原因", "共识", null));

        assertThrows(NotFoundException.class, () -> service.getByLetter(2L, 1L));
        assertThrows(NotFoundException.class, () -> service.update(2L, 5L,
                new MemoUpdateRequest(1, "偷改", null, null, null)));

        service.submit(1L, 5L);
        assertEquals("PENDING_REVIEW", service.getByLetter(2L, 1L).status());
        assertEquals("ACTIVE", service.confirm(2L, 5L).status());
    }

    @Test
    void oldMemoResumesWithSamePartnerButNotWithOtherPartner() {
        activateMemo();
        alice.setPartnerId(null);
        bob.setPartnerId(null);
        alice.setRelationshipStatus("UNBOUND_HISTORY");
        bob.setRelationshipStatus("UNBOUND_HISTORY");
        assertEquals("ACTIVE", service.getByLetter(1L, 1L).status());
        assertEquals("ACTIVE", service.getByLetter(2L, 1L).status());
        assertThrows(ConflictException.class, () -> service.proposeMemoRevision(1L, 5L,
                new MemoRevisionProposalRequest("旧事件", "修改", "修改", null)));

        alice.setPartnerId(2L);
        bob.setPartnerId(1L);
        alice.setRelationshipStatus("BOUND");
        bob.setRelationshipStatus("BOUND");
        assertEquals("PENDING_REVIEW", service.proposeMemoRevision(1L, 5L,
                new MemoRevisionProposalRequest("旧事件", "修改", "修改", null)).status());

        alice.setPartnerId(3L);
        bob.setPartnerId(4L);
        assertThrows(ConflictException.class, () -> service.createCommitment(1L, 5L,
                new CommitmentCreateRequest("新增承诺")));
    }

    @Test
    void reviewerCanSuggestChangesInsteadOfOnlyAcceptingOrRejecting() {
        activateMemo();
        var proposed = service.proposeMemoRevision(2L, 5L,
                new MemoRevisionProposalRequest("事件", "更准确的原因", "调整后的共识", null));

        assertEquals("PENDING_REVIEW", proposed.status());
        assertThrows(ForbiddenException.class, () -> service.confirm(2L, 5L));
        var accepted = service.confirm(1L, 5L);
        assertEquals("ACTIVE", accepted.status());
        assertEquals("调整后的共识", accepted.commonGround());
        assertTrue(accepted.revisions().stream().anyMatch(item -> item.changedBy().equals(2L)));
    }

    @Test
    void returnedMemoDraftChangesStayPrivateUntilResubmitted() {
        service.create(1L, new MemoCreateRequest(1L, "事件", "原因", "原共识", null));
        service.submit(1L, 5L);
        service.returnMemo(2L, 5L, new ReviewReturnRequest("希望再具体一些"));
        service.update(1L, 5L, new MemoUpdateRequest(1, "事件", "原因", "私下修改中", null));

        assertEquals("私下修改中", service.getByLetter(1L, 1L).commonGround());
        var partnerView = service.getByLetter(2L, 1L);
        assertEquals("RETURNED", partnerView.status());
        assertEquals("原共识", partnerView.commonGround());
        assertFalse(partnerView.revisions().stream().anyMatch(item -> "私下修改中".equals(item.commonGround())));
    }

    @Test
    void commitmentAlwaysBelongsToCurrentUserAndIsPrivateBeforeSubmission() {
        activateMemo();
        var draft = service.createCommitment(1L, 5L, new CommitmentCreateRequest("我会提前说明"));

        assertEquals(1L, draft.ownerId());
        assertEquals("PRIVATE_DRAFT", draft.status());
        assertTrue(service.getByLetter(2L, 1L).commitments().isEmpty());

        service.submitCommitment(1L, draft.commitmentId());
        assertEquals(1, service.getByLetter(2L, 1L).commitments().size());
        var adjusted = service.proposeCommitmentRevision(2L, draft.commitmentId(),
                new CommitmentRevisionRequest("忙时提前说明并给出预计时间"));
        assertEquals("PENDING_REVIEW", adjusted.status());
        assertEquals("ACTIVE", service.reviewCommitmentRevision(
                1L, adjusted.pendingRevisionId(), true).status());
    }

    @Test
    void onlyNeverSubmittedPrivateCommitmentCanBeDeleted() {
        activateMemo();
        var privateDraft = service.createCommitment(1L, 5L, new CommitmentCreateRequest("草稿"));
        service.deleteCommitmentDraft(1L, privateDraft.commitmentId());
        assertTrue(commitments.isEmpty());

        var submitted = service.createCommitment(1L, 5L, new CommitmentCreateRequest("已提交内容"));
        service.submitCommitment(1L, submitted.commitmentId());
        service.returnCommitmentRevision(2L, submitted.currentRevisionId(), new ReviewReturnRequest("再想想"));
        assertThrows(ConflictException.class,
                () -> service.deleteCommitmentDraft(1L, submitted.commitmentId()));
        assertFalse(commitments.isEmpty());
    }

    private void activateMemo() {
        service.create(1L, new MemoCreateRequest(1L, "事件", "原因", "共识", null));
        service.submit(1L, 5L);
        service.confirm(2L, 5L);
    }

    private static EmotionLetterEntity letter() {
        EmotionLetterEntity entity = new EmotionLetterEntity();
        entity.setId(1L);
        entity.setSenderId(1L);
        entity.setReceiverId(2L);
        entity.setThreadId("thread-1");
        entity.setTitle("原始标题");
        entity.setStatus("READ");
        entity.setIsDeleted(0);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
