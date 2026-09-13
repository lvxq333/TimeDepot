package com.couple.service.memo.service.impl;

import com.couple.common.exception.BadRequestException;
import com.couple.common.exception.NotFoundException;
import com.couple.service.letter.entity.EmotionLetterEntity;
import com.couple.service.letter.mapper.EmotionLetterMapper;
import com.couple.service.letter.service.LetterService;
import com.couple.service.letter.vo.LetterDetailView;
import com.couple.service.memo.service.MemoEventService;
import com.couple.service.memo.service.MemoService;
import com.couple.service.memo.vo.CommitmentRevisionView;
import com.couple.service.memo.vo.CommitmentView;
import com.couple.service.memo.vo.ConsensusMemoView;
import com.couple.service.memo.vo.MemoEventDetailView;
import com.couple.service.memo.vo.MemoEventPageResponse;
import com.couple.service.memo.vo.MemoEventSummaryView;
import com.couple.service.memo.vo.MemoRevisionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 从已送达的情绪信中实时聚合事件，避免额外维护一份容易失真的事件数据。 */
@Service
public class MemoEventServiceImpl implements MemoEventService {
    private static final Set<String> SHARED_STATUSES = Set.of("SENT", "READ", "RESOLVED");

    private final EmotionLetterMapper letterMapper;
    private final LetterService letterService;
    private final MemoService memoService;

    public MemoEventServiceImpl(EmotionLetterMapper letterMapper,
                                LetterService letterService,
                                MemoService memoService) {
        this.letterMapper = letterMapper;
        this.letterService = letterService;
        this.memoService = memoService;
    }

    @Override
    @Transactional(readOnly = true)
    public MemoEventPageResponse list(Long userId, int page, int pageSize) {
        validatePage(page, pageSize);
        long offset = (long) (page - 1) * pageSize;
        Map<String, List<EmotionLetterEntity>> grouped = groupLetters(
                letterMapper.selectSharedEventPage(userId, offset, pageSize));
        List<MemoEventSummaryView> events = grouped.values().stream()
                .map(letters -> summarize(userId, letters, visibleMemo(userId, letters.get(0).getId())))
                .sorted((left, right) -> right.lastReplyAt().compareTo(left.lastReplyAt()))
                .toList();
        return new MemoEventPageResponse(events, letterMapper.countSharedEvents(userId), page, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public MemoEventDetailView detail(Long userId, String threadId) {
        if (threadId == null || threadId.isBlank()) throw new BadRequestException("事件编号不能为空");
        List<EmotionLetterEntity> shared = letterMapper.selectSharedEventThread(userId, threadId);
        if (shared.isEmpty()) throw new NotFoundException("共同备忘事件不存在");

        Long rootLetterId = shared.get(0).getId();
        List<LetterDetailView> timeline = letterService.thread(userId, rootLetterId).letters().stream()
                .filter(letter -> SHARED_STATUSES.contains(letter.status()))
                .toList();
        ConsensusMemoView memo = visibleMemo(userId, rootLetterId);
        return new MemoEventDetailView(summarize(userId, shared, memo), timeline, memo);
    }

    private Map<String, List<EmotionLetterEntity>> groupLetters(List<EmotionLetterEntity> letters) {
        Map<String, List<EmotionLetterEntity>> grouped = new LinkedHashMap<>();
        for (EmotionLetterEntity letter : letters) {
            grouped.computeIfAbsent(letter.getThreadId(), ignored -> new ArrayList<>()).add(letter);
        }
        return grouped;
    }

    private MemoEventSummaryView summarize(Long userId, List<EmotionLetterEntity> letters,
                                           ConsensusMemoView memo) {
        EmotionLetterEntity root = letters.get(0);
        EmotionLetterEntity latest = letters.get(letters.size() - 1);
        int unread = (int) letters.stream().filter(letter -> Objects.equals(userId, letter.getReceiverId())
                && "SENT".equals(letter.getStatus())).count();
        return new MemoEventSummaryView(root.getThreadId(), root.getId(),
                root.getTitle() == null || root.getTitle().isBlank() ? "未命名情绪事件" : root.getTitle(),
                root.getSenderId(), root.getCreatedAt(), latest.getCreatedAt(), letters.size(), unread,
                memo == null ? "NONE" : memo.status(), pendingActionCount(userId, memo));
    }

    private ConsensusMemoView visibleMemo(Long userId, Long rootLetterId) {
        try {
            return memoService.getByLetter(userId, rootLetterId);
        } catch (NotFoundException exception) {
            return null;
        }
    }

    private int pendingActionCount(Long userId, ConsensusMemoView memo) {
        if (memo == null) return 0;
        int count = 0;
        if ("PENDING_REVIEW".equals(memo.status())) {
            MemoRevisionView revision = memo.revisions().stream()
                    .filter(item -> Objects.equals(item.revisionId(), memo.pendingRevisionId()))
                    .findFirst().orElse(null);
            if (revision != null && !Objects.equals(userId, revision.changedBy())) count++;
        }
        if ("RESOLUTION_PENDING".equals(memo.status())
                && !Objects.equals(userId, memo.resolutionRequestedBy())) count++;
        for (CommitmentView commitment : memo.commitments()) {
            CommitmentRevisionView revision = commitment.revisions().stream()
                    .filter(item -> Objects.equals(item.revisionId(), commitment.pendingRevisionId()))
                    .findFirst().orElse(null);
            if (revision != null && !Objects.equals(userId, revision.proposedBy())) count++;
        }
        return count;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1) throw new BadRequestException("页码必须大于等于 1");
        if (pageSize < 1 || pageSize > 50) throw new BadRequestException("每页数量必须为 1~50");
    }
}
