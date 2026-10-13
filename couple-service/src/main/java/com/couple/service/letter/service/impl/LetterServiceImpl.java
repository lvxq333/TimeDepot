package com.couple.service.letter.service.impl;

import com.couple.common.exception.BadRequestException;
import com.couple.common.exception.BusinessException;
import com.couple.common.exception.ConflictException;
import com.couple.common.exception.ForbiddenException;
import com.couple.common.exception.NotFoundException;
import com.couple.common.exception.UnauthorizedException;
import com.couple.service.letter.dto.LetterCreateRequest;
import com.couple.service.letter.dto.LetterUpdateRequest;
import com.couple.service.letter.entity.EmotionLetterEntity;
import com.couple.service.letter.entity.LetterLogEntity;
import com.couple.service.letter.entity.LetterThreadEntity;
import com.couple.service.letter.mapper.EmotionLetterMapper;
import com.couple.service.letter.mapper.LetterLogMapper;
import com.couple.service.letter.mapper.LetterVisibilityMapper;
import com.couple.service.letter.mapper.LetterThreadMapper;
import com.couple.service.annotation.mapper.AnnotationMapper;
import com.couple.service.letter.service.LetterService;
import com.couple.service.letter.vo.LetterDetailView;
import com.couple.service.letter.vo.LetterOperationResponse;
import com.couple.service.letter.vo.LetterPageResponse;
import com.couple.service.letter.vo.LetterSummaryView;
import com.couple.service.letter.vo.LetterThreadView;
import com.couple.service.letter.vo.LetterThreadPageResponse;
import com.couple.service.letter.vo.LetterThreadSummaryView;
import com.couple.service.notification.RealtimeNotificationEvent;
import com.couple.service.user.entity.UserEntity;
import com.couple.service.user.mapper.UserMapper;
import com.couple.service.user.service.RelationshipAccess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 情绪信业务实现，以数据库状态和冷静期结束时间作为最终一致性依据。 */
@Service
public class LetterServiceImpl implements LetterService {

    private static final String DRAFT = "DRAFT";
    private static final String COOLDOWN = "COOLDOWN";
    private static final String SENT = "SENT";
    private static final String READ = "READ";
    private static final String RESOLVED = "RESOLVED";
    private static final String BOUND = "BOUND";
    private static final Set<String> STATUSES = Set.of(DRAFT, COOLDOWN, SENT, READ, RESOLVED);
    private static final Set<String> EMOTION_TAGS = Set.of("angry", "sad", "doubt", "hurt", "suggest");
    private static final Set<String> THREAD_STATUSES = Set.of("OPEN", "REOPENED", "RESOLVED", "CLOSED", DRAFT, COOLDOWN);

    private final EmotionLetterMapper letterMapper;
    private final LetterVisibilityMapper visibilityMapper;
    private final LetterLogMapper logMapper;
    private final UserMapper userMapper;
    private final long cooldownSeconds;
    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;
    @Autowired(required = false)
    private LetterThreadMapper threadMapper;
    @Autowired(required = false)
    private AnnotationMapper annotationMapper;

    public LetterServiceImpl(EmotionLetterMapper letterMapper,
                             LetterLogMapper logMapper,
                             UserMapper userMapper,
                             LetterVisibilityMapper visibilityMapper,
                             @Value("${letter.cooldown-seconds:300}") long cooldownSeconds) {
        this.letterMapper = letterMapper;
        this.visibilityMapper = visibilityMapper;
        this.logMapper = logMapper;
        this.userMapper = userMapper;
        this.cooldownSeconds = Math.max(1, cooldownSeconds);
    }

    /** 为当前已绑定用户创建草稿；回复信会继承原信话题并发送给原信作者。 */
    @Override
    @Transactional
    public LetterOperationResponse create(Long userId, LetterCreateRequest request) {
        UserEntity sender = requireBoundUser(userId);
        UserEntity receiver = requireUser(sender.getPartnerId());
        RelationshipAccess.requireCurrentPair(userMapper, userId, receiver.getId());
        EmotionLetterEntity parent = null;
        if (request.replyToLetterId() != null) {
            parent = requireLockedLetter(request.replyToLetterId());
            requireReplyTarget(userId, parent, receiver);
            RelationshipAccess.requireWritable(userMapper, parent);
            receiver = requireUser(parent.getSenderId());
        }
        if (!Objects.equals(receiver.getPartnerId(), userId) || !BOUND.equals(receiver.getRelationshipStatus())) {
            throw new ConflictException("伴侣关系状态异常，请重新确认绑定状态");
        }

        LocalDateTime now = LocalDateTime.now();
        EmotionLetterEntity letter = new EmotionLetterEntity();
        letter.setSenderId(userId);
        letter.setReceiverId(receiver.getId());
        letter.setThreadId(parent == null || parent.getThreadId() == null
                ? UUID.randomUUID().toString() : parent.getThreadId());
        letter.setReplyToLetterId(request.replyToLetterId());
        applyContent(letter, request.title(), request.content(), request.emotionTag(), request.intensity());
        letter.setStatus(DRAFT);
        letter.setIsRead(0);
        letter.setIsDeleted(0);
        letter.setCreatedAt(now);
        letter.setUpdatedAt(now);
        if (letterMapper.insert(letter) != 1) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "草稿保存失败，请稍后重试");
        }
        if (parent == null) createThread(letter, now);
        writeLog(letter.getId(), userId, "CREATE", "创建情绪信草稿", now);
        return operation(letter, null, "已保存为草稿");
    }

    /** 仅允许发送方修改草稿，发送后的正文永久保持不变。 */
    @Override
    @Transactional
    public LetterOperationResponse update(Long userId, Long letterId, LetterUpdateRequest request) {
        EmotionLetterEntity letter = requireLockedLetter(letterId);
        requireSender(userId, letter);
        RelationshipAccess.requireWritable(userMapper, letter);
        requireStatus(letter, DRAFT, "只有草稿可以修改");
        applyContent(letter, request.title(), request.content(), request.emotionTag(), request.intensity());
        letter.setUpdatedAt(LocalDateTime.now());
        updateOrFail(letter);
        if (letter.getReplyToLetterId() == null) updateThreadTitle(letter);
        writeLog(letterId, userId, "UPDATE", "修改情绪信草稿", letter.getUpdatedAt());
        return operation(letter, null, "草稿已更新");
    }

    /** 将草稿提交到固定冷静期，结束时间持久化到数据库。 */
    @Override
    @Transactional
    public LetterOperationResponse submit(Long userId, Long letterId) {
        EmotionLetterEntity letter = requireLockedLetter(letterId);
        requireSender(userId, letter);
        RelationshipAccess.requireWritable(userMapper, letter);
        if (COOLDOWN.equals(letter.getStatus())) {
            return operation(letter, remainingSeconds(letter), "信件已在冷静期中");
        }
        requireStatus(letter, DRAFT, "当前状态不允许提交");
        LocalDateTime now = LocalDateTime.now();
        letter.setStatus(COOLDOWN);
        letter.setCoolDownEnd(now.plusSeconds(cooldownSeconds));
        letter.setUpdatedAt(now);
        updateOrFail(letter);
        writeLog(letterId, userId, "SUBMIT", "提交冷静期", now);

        // TODO(LLM): 后期由模型给出缩短冷静期的建议；必须保留最低时长、用户确认和审计记录。
        return operation(letter, Math.toIntExact(cooldownSeconds), "已进入冷静期");
    }

    /** 冷静期间由发送方撤回，恢复到可编辑草稿。 */
    @Override
    @Transactional
    public LetterOperationResponse revert(Long userId, Long letterId) {
        EmotionLetterEntity letter = requireLockedLetter(letterId);
        requireSender(userId, letter);
        RelationshipAccess.requireWritable(userMapper, letter);
        requireStatus(letter, COOLDOWN, "只有冷静期中的信件可以退回修改");
        LocalDateTime now = LocalDateTime.now();
        letter.setStatus(DRAFT);
        letter.setCoolDownEnd(null);
        letter.setUpdatedAt(now);
        updateOrFail(letter);
        writeLog(letterId, userId, "REVERT", "退回草稿继续修改", now);
        return operation(letter, null, "已退回到草稿状态");
    }

    /** 冷静期结束后由发送方确认发送。 */
    @Override
    @Transactional
    public LetterOperationResponse send(Long userId, Long letterId) {
        EmotionLetterEntity letter = requireLockedLetter(letterId);
        requireSender(userId, letter);
        requireStatus(letter, COOLDOWN, "只有冷静期中的信件可以发送");
        int remaining = remainingSeconds(letter);
        if (remaining > 0) {
            throw new ConflictException("冷静期还剩 " + remaining + " 秒，请稍后再发送");
        }
        RelationshipAccess.requireWritable(userMapper, letter);
        UserEntity sender = requireBoundUser(userId);
        if (!Objects.equals(sender.getPartnerId(), letter.getReceiverId())) {
            throw new ConflictException("原收件人已不是当前伴侣，无法发送");
        }
        UserEntity receiver = requireUser(letter.getReceiverId());
        if (!BOUND.equals(receiver.getRelationshipStatus())
                || !Objects.equals(receiver.getPartnerId(), userId)) {
            throw new ConflictException("伴侣关系已解除，无法发送");
        }
        LocalDateTime now = LocalDateTime.now();
        letter.setStatus(SENT);
        letter.setCoolDownEnd(null);
        letter.setSentAt(now);
        letter.setUpdatedAt(now);
        updateOrFail(letter);
        reopenThreadAfterNewMessage(letter.getThreadId(), now);
        writeLog(letterId, userId, "SEND", "发送情绪信", now);
        publish("LETTER_SENT", letter.getReceiverId(), userId, letter,
                Map.of("title", letter.getTitle(), "threadId", letter.getThreadId()));
        return operation(letter, null, "信件已发送");
    }

    /** 分页查询当前用户可见的信件，接收方看不到尚未发送的内容。 */
    @Override
    @Transactional(readOnly = true)
    public LetterPageResponse list(Long userId, int page, int pageSize, String status,
                                   String direction, String emotionTag) {
        requireUser(userId);
        if (page < 1) throw new BadRequestException("页码不能小于 1");
        if (pageSize < 1 || pageSize > 50) throw new BadRequestException("每页数量必须为 1~50");
        String normalizedDirection = normalizeDirection(direction);
        List<String> statuses = normalizeStatuses(status);
        String normalizedEmotionTag = normalizeEmotionTag(emotionTag, true);
        long offset = (long) (page - 1) * pageSize;
        List<EmotionLetterEntity> letters = letterMapper.selectVisiblePage(
                userId, normalizedDirection, statuses, normalizedEmotionTag, offset, pageSize);
        List<LetterSummaryView> records = letters.stream()
                .map(letter -> toSummary(userId, letter)).toList();
        long total = letterMapper.countVisible(userId, normalizedDirection, statuses, normalizedEmotionTag);
        return new LetterPageResponse(records, total, page, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public LetterThreadPageResponse listThreads(Long userId, int page, int pageSize, String status,
                                                String direction, String emotionTag, String keyword,
                                                LocalDate startDate, LocalDate endDate) {
        requireUser(userId);
        if (page < 1) throw new BadRequestException("页码不能小于 1");
        if (pageSize < 1 || pageSize > 50) throw new BadRequestException("每页数量必须为 1~50");
        String normalizedDirection = normalizeDirection(direction);
        if (normalizedDirection.isEmpty()) normalizedDirection = null;
        String normalizedStatus = normalizeThreadStatus(status);
        String normalizedEmotionTag = normalizeEmotionTag(emotionTag, true);
        String normalizedKeyword = normalizeKeyword(keyword);
        validateThreadFilters(startDate, endDate);
        LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        long offset = (long) (page - 1) * pageSize;
        List<EmotionLetterEntity> letters = letterMapper.selectVisibleThreadPage(
                userId, normalizedDirection, normalizedStatus, normalizedEmotionTag, normalizedKeyword,
                startAt, endAt, offset, pageSize);
        Map<String, List<EmotionLetterEntity>> grouped = new LinkedHashMap<>();
        for (EmotionLetterEntity item : letters) {
            grouped.computeIfAbsent(item.getThreadId(), ignored -> new ArrayList<>()).add(item);
        }
        Map<String, LetterThreadEntity> threads = new HashMap<>();
        if (threadMapper != null && !grouped.isEmpty()) {
            for (LetterThreadEntity item : threadMapper.selectByThreadIds(new ArrayList<>(grouped.keySet()))) {
                threads.put(item.getThreadId(), item);
            }
        }
        Map<Long, Integer> annotationCounts = annotationCounts(letters);
        List<LetterThreadSummaryView> records = grouped.entrySet().stream().map(entry -> {
            List<EmotionLetterEntity> items = entry.getValue();
            EmotionLetterEntity root = items.get(0);
            EmotionLetterEntity latest = items.get(items.size() - 1);
            LetterThreadEntity state = threads.get(entry.getKey());
            String threadStatus = state == null ? deriveThreadStatus(items) : state.getStatus();
            String eventStatus = Set.of("RESOLVED", "CLOSED").contains(threadStatus)
                    ? threadStatus : Set.of(DRAFT, COOLDOWN).contains(latest.getStatus())
                    ? latest.getStatus() : threadStatus;
            int unread = (int) items.stream().filter(item -> Objects.equals(item.getReceiverId(), userId)
                    && Objects.equals(item.getIsRead(), 0)).count();
            int annotations = items.stream().mapToInt(item -> annotationCounts.getOrDefault(item.getId(), 0)).sum();
            LocalDateTime lastActivity = items.stream().map(item -> item.getSentAt() == null
                    ? item.getCreatedAt() : item.getSentAt()).max(LocalDateTime::compareTo).orElse(root.getCreatedAt());
            String title = state == null || state.getTitle() == null ? root.getTitle() : state.getTitle();
            EmotionLetterEntity matched = findMatchedLetter(items, normalizedKeyword, normalizedEmotionTag,
                    startAt, endAt);
            return new LetterThreadSummaryView(entry.getKey(), title, eventStatus, items.size(), unread,
                    annotations, lastActivity, toSummary(userId, root), toSummary(userId, latest),
                    matched.getId(), matchSnippet(matched, normalizedKeyword));
        }).toList();
        long total = letterMapper.countVisibleThreads(userId, normalizedDirection, normalizedStatus,
                normalizedEmotionTag, normalizedKeyword, startAt, endAt);
        return new LetterThreadPageResponse(records, total, page, pageSize);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String value = keyword.trim();
        if (value.length() > 100) throw new BadRequestException("搜索关键词不能超过 100 个字符");
        return value;
    }

    private void validateThreadFilters(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BadRequestException("开始日期不能晚于结束日期");
        }
    }

    private EmotionLetterEntity findMatchedLetter(List<EmotionLetterEntity> items, String keyword,
                                                   String emotionTag, LocalDateTime startAt,
                                                   LocalDateTime endAt) {
        for (int index = items.size() - 1; index >= 0; index--) {
            EmotionLetterEntity item = items.get(index);
            boolean keywordMatches = keyword == null
                    || containsIgnoreCase(item.getTitle(), keyword) || containsIgnoreCase(item.getContent(), keyword);
            boolean emotionMatches = emotionTag == null || emotionTag.equals(item.getEmotionTag());
            boolean dateMatches = (startAt == null || !item.getCreatedAt().isBefore(startAt))
                    && (endAt == null || item.getCreatedAt().isBefore(endAt));
            if (keywordMatches && emotionMatches && dateMatches) return item;
        }
        return items.get(items.size() - 1);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private String matchSnippet(EmotionLetterEntity item, String keyword) {
        if (keyword == null) return null;
        String source = containsIgnoreCase(item.getContent(), keyword) ? item.getContent() : item.getTitle();
        if (source == null || source.isBlank()) return null;
        String lower = source.toLowerCase(Locale.ROOT);
        int position = lower.indexOf(keyword.toLowerCase(Locale.ROOT));
        int start = Math.max(0, position - 24);
        int end = Math.min(source.length(), position + keyword.length() + 36);
        return (start > 0 ? "…" : "") + source.substring(start, end) + (end < source.length() ? "…" : "");
    }

    /** 只有发送方或已经实际收到信件的接收方可以查看详情。 */
    @Override
    @Transactional(readOnly = true)
    public LetterDetailView detail(Long userId, Long letterId) {
        EmotionLetterEntity letter = letterMapper.selectById(letterId);
        if (letter == null) throw new NotFoundException("情绪信不存在");
        if (visibilityMapper.isHidden(letterId, userId) > 0) throw new NotFoundException("信件已从你的历史中移除");
        boolean sender = Objects.equals(userId, letter.getSenderId());
        boolean receiver = Objects.equals(userId, letter.getReceiverId());
        if (!sender && !receiver) throw new ForbiddenException("无权查看这封情绪信");
        if (receiver && !Set.of(SENT, READ, RESOLVED).contains(letter.getStatus())) {
            throw new ForbiddenException("这封情绪信尚未发送给你");
        }
        return toDetail(userId, letter);
    }

    /** 查询当前用户可见的话题时间线；未发送的对方草稿和冷静期信不会泄露。 */
    @Override
    @Transactional(readOnly = true)
    public LetterThreadView thread(Long userId, Long letterId) {
        EmotionLetterEntity anchor = letterMapper.selectById(letterId);
        if (anchor == null) throw new NotFoundException("情绪信不存在");
        detail(userId, letterId);
        List<EmotionLetterEntity> letters = letterMapper.selectVisibleThread(userId, anchor.getThreadId());
        List<LetterDetailView> views = letters.stream().map(letter -> toDetail(userId, letter)).toList();
        String title = views.isEmpty() ? anchor.getTitle() : views.get(0).title();
        LetterThreadEntity state = threadMapper == null ? null : threadMapper.selectById(anchor.getThreadId());
        return new LetterThreadView(anchor.getThreadId(), title,
                state == null ? deriveThreadStatus(letters) : state.getStatus(), views, annotationCounts(letters));
    }

    /** 接收方首次打开信件时标记已读；重复调用保持幂等。 */
    @Override
    @Transactional
    public LetterOperationResponse read(Long userId, Long letterId) {
        EmotionLetterEntity letter = requireLockedLetter(letterId);
        requireReceiver(userId, letter);
        if (visibilityMapper.isHidden(letterId, userId) > 0) throw new NotFoundException("信件已从你的历史中移除");
        if (!Set.of(SENT, READ, RESOLVED).contains(letter.getStatus())) {
            throw new ConflictException("这封信尚未发送给你");
        }
        if (!RelationshipAccess.isWritableLocked(userMapper, letter)) {
            return operation(letter, null, "历史信件仅可查看");
        }
        if (READ.equals(letter.getStatus()) || RESOLVED.equals(letter.getStatus())) {
            return operation(letter, null, "信件已读");
        }
        requireStatus(letter, SENT, "当前状态不能标记已读");
        LocalDateTime now = LocalDateTime.now();
        letter.setStatus(READ);
        letter.setIsRead(1);
        letter.setReadAt(now);
        letter.setUpdatedAt(now);
        updateOrFail(letter);
        writeLog(letterId, userId, "READ", "接收方首次打开信件", now);
        publish("LETTER_READ", letter.getSenderId(), userId, letter, Map.of());
        return operation(letter, null, "信件已读");
    }

    /** 仅接收方可将单封信标记为已处理；线程级解决状态由共识备忘录维护。 */
    @Override
    @Transactional
    public LetterOperationResponse resolve(Long userId, Long letterId) {
        EmotionLetterEntity letter = requireLockedLetter(letterId);
        requireReceiver(userId, letter);
        if (visibilityMapper.isHidden(letterId, userId) > 0) throw new NotFoundException("信件已从你的历史中移除");
        RelationshipAccess.requireWritable(userMapper, letter);
        if (RESOLVED.equals(letter.getStatus())) {
            return operation(letter, null, "信件已标记为已解决");
        }
        requireStatus(letter, READ, "只有已读信件可以标记为已解决");
        LocalDateTime now = LocalDateTime.now();
        letter.setStatus(RESOLVED);
        letter.setResolvedAt(now);
        letter.setUpdatedAt(now);
        updateOrFail(letter);
        resolveThread(letter.getThreadId(), userId, now);
        writeLog(letterId, userId, "RESOLVE", "接收方确认情绪问题已解决", now);
        publish("LETTER_RESOLVED", letter.getSenderId(), userId, letter, Map.of());

        // TODO(LLM): 后期可温和建议发起线程解决申请，但必须由双方在备忘录流程中确认。
        return operation(letter, null, "已将这封信标记为已解决");
    }

    /** 未发送信件由作者删除；已送达信件只从当前用户视角移除。 */
    @Override
    @Transactional
    public LetterOperationResponse delete(Long userId, Long letterId) {
        EmotionLetterEntity letter = requireLockedLetter(letterId);
        if (Set.of(SENT, READ, RESOLVED).contains(letter.getStatus())) {
            if (!Objects.equals(userId, letter.getSenderId())
                    && !Objects.equals(userId, letter.getReceiverId())) {
                throw new ForbiddenException("无权删除这封情绪信");
            }
            visibilityMapper.hide(letterId, userId);
            return operation(letter, null, "已从你的历史中移除，对方仍可查看");
        }
        requireSender(userId, letter);
        RelationshipAccess.requireWritable(userMapper, letter);
        if (!Set.of(DRAFT, COOLDOWN).contains(letter.getStatus())) {
            throw new ConflictException("当前信件状态不能删除");
        }
        LocalDateTime now = LocalDateTime.now();
        writeLog(letterId, userId, "DELETE", "删除未发送的情绪信", now);
        if (letterMapper.deleteById(letterId) != 1) {
            throw new ConflictException("草稿状态已发生变化，请刷新后重试");
        }
        if (letter.getReplyToLetterId() == null && threadMapper != null) threadMapper.deleteById(letter.getThreadId());
        return new LetterOperationResponse(letterId, DRAFT, null, null,
                null, null, null, "删除成功");
    }

    private EmotionLetterEntity requireLockedLetter(Long letterId) {
        EmotionLetterEntity letter = letterMapper.selectByIdForUpdate(letterId);
        if (letter == null) throw new NotFoundException("情绪信不存在");
        return letter;
    }

    private void createThread(EmotionLetterEntity letter, LocalDateTime now) {
        if (threadMapper == null) return;
        LetterThreadEntity thread = new LetterThreadEntity();
        thread.setThreadId(letter.getThreadId());
        thread.setTitle(letter.getTitle());
        thread.setParticipantAId(Math.min(letter.getSenderId(), letter.getReceiverId()));
        thread.setParticipantBId(Math.max(letter.getSenderId(), letter.getReceiverId()));
        thread.setStatus("OPEN");
        thread.setCreatedAt(now);
        thread.setUpdatedAt(now);
        if (threadMapper.insert(thread) != 1) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "事件创建失败，请稍后重试");
        }
    }

    private void reopenThreadAfterNewMessage(String threadId, LocalDateTime now) {
        if (threadMapper == null) return;
        LetterThreadEntity thread = threadMapper.selectByIdForUpdate(threadId);
        if (thread == null) return;
        if (Set.of("RESOLVED", "CLOSED").contains(thread.getStatus())) {
            thread.setStatus("REOPENED");
            thread.setReopenedAt(now);
        }
        thread.setUpdatedAt(now);
        threadMapper.updateById(thread);
    }

    private void resolveThread(String threadId, Long userId, LocalDateTime now) {
        if (threadMapper == null) return;
        LetterThreadEntity thread = threadMapper.selectByIdForUpdate(threadId);
        if (thread == null) return;
        thread.setStatus("RESOLVED");
        thread.setResolvedBy(userId);
        thread.setResolvedAt(now);
        thread.setUpdatedAt(now);
        threadMapper.updateById(thread);
    }

    private void updateThreadTitle(EmotionLetterEntity letter) {
        if (threadMapper == null) return;
        LetterThreadEntity thread = threadMapper.selectById(letter.getThreadId());
        if (thread == null) return;
        thread.setTitle(letter.getTitle());
        thread.setUpdatedAt(letter.getUpdatedAt());
        threadMapper.updateById(thread);
    }

    private Map<Long, Integer> annotationCounts(List<EmotionLetterEntity> letters) {
        Map<Long, Integer> counts = new HashMap<>();
        if (annotationMapper == null || letters.isEmpty()) return counts;
        annotationMapper.countTopLevelByLetterIds(letters.stream().map(EmotionLetterEntity::getId).toList())
                .forEach(item -> counts.put(item.getLetterId(), Math.toIntExact(item.getAnnotationCount())));
        return counts;
    }

    private String deriveThreadStatus(List<EmotionLetterEntity> letters) {
        LocalDateTime latestActivity = letters.stream().map(item -> item.getSentAt() == null
                ? item.getCreatedAt() : item.getSentAt()).max(LocalDateTime::compareTo).orElse(LocalDateTime.MIN);
        LocalDateTime latestResolution = letters.stream().map(EmotionLetterEntity::getResolvedAt)
                .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        if (latestResolution == null) return "OPEN";
        return latestResolution.compareTo(latestActivity) >= 0 ? "RESOLVED" : "REOPENED";
    }

    private UserEntity requireUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) throw new UnauthorizedException("用户不存在或已注销");
        return user;
    }

    private UserEntity requireBoundUser(Long userId) {
        UserEntity user = requireUser(userId);
        if (!BOUND.equals(user.getRelationshipStatus()) || user.getPartnerId() == null) {
            throw new ConflictException("请先绑定伴侣再写情绪信");
        }
        return user;
    }

    private void requireSender(Long userId, EmotionLetterEntity letter) {
        if (!Objects.equals(userId, letter.getSenderId())) {
            throw new ForbiddenException("只有发送方可以执行此操作");
        }
    }

    private void requireReceiver(Long userId, EmotionLetterEntity letter) {
        if (!Objects.equals(userId, letter.getReceiverId())) {
            throw new ForbiddenException("只有这封信的接收方可以执行此操作");
        }
    }

    private void requireReplyTarget(Long userId, EmotionLetterEntity parent, UserEntity currentPartner) {
        if (!Objects.equals(userId, parent.getReceiverId())
                || !Set.of(SENT, READ, RESOLVED).contains(parent.getStatus())) {
            throw new ForbiddenException("只能回复已经收到的伴侣信件");
        }
        if (!Objects.equals(currentPartner.getId(), parent.getSenderId())
                || !Objects.equals(currentPartner.getPartnerId(), userId)
                || !BOUND.equals(currentPartner.getRelationshipStatus())) {
            throw new ConflictException("原信发送方已不是当前伴侣，无法回复");
        }
    }

    private void requireStatus(EmotionLetterEntity letter, String expected, String message) {
        if (!expected.equals(letter.getStatus())) throw new ConflictException(message);
    }

    private void applyContent(EmotionLetterEntity letter, String title, String content,
                              String emotionTag, Integer intensity) {
        letter.setTitle(title == null || title.isBlank() ? null : title.trim());
        letter.setContent(content.trim());
        letter.setEmotionTag(normalizeEmotionTag(emotionTag, false));
        letter.setIntensity(intensity == null ? 5 : intensity);
    }

    private String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) return "";
        String value = direction.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("sent", "inbox").contains(value)) {
            throw new BadRequestException("direction 只支持 sent 或 inbox");
        }
        return value;
    }

    private List<String> normalizeStatuses(String status) {
        if (status == null || status.isBlank()) return List.of();
        List<String> values = Arrays.stream(status.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT)).distinct().toList();
        if (values.isEmpty() || values.stream().anyMatch(value -> !STATUSES.contains(value))) {
            throw new BadRequestException("信件状态筛选值不合法");
        }
        return values;
    }

    private String normalizeThreadStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!THREAD_STATUSES.contains(value)) throw new BadRequestException("事件状态筛选值不合法");
        return value;
    }

    private String normalizeEmotionTag(String emotionTag, boolean nullable) {
        if (emotionTag == null || emotionTag.isBlank()) {
            if (nullable) return null;
            throw new BadRequestException("请选择情绪标签");
        }
        String value = emotionTag.trim().toLowerCase(Locale.ROOT);
        if (!EMOTION_TAGS.contains(value)) throw new BadRequestException("情绪标签不合法");
        return value;
    }

    private int remainingSeconds(EmotionLetterEntity letter) {
        if (letter.getCoolDownEnd() == null) return 0;
        long seconds = Duration.between(LocalDateTime.now(), letter.getCoolDownEnd()).toSeconds();
        return seconds <= 0 ? 0 : Math.toIntExact(Math.min(Integer.MAX_VALUE, seconds + 1));
    }

    private void updateOrFail(EmotionLetterEntity letter) {
        if (letterMapper.updateById(letter) != 1) {
            throw new ConflictException("信件状态已发生变化，请刷新后重试");
        }
    }

    private void writeLog(Long letterId, Long userId, String action, String detail, LocalDateTime now) {
        LetterLogEntity log = new LetterLogEntity();
        log.setLetterId(letterId);
        log.setOperatorId(userId);
        log.setAction(action);
        log.setDetail(detail);
        log.setCreatedAt(now);
        if (logMapper.insert(log) != 1) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "信件操作记录失败");
        }
    }

    private LetterOperationResponse operation(EmotionLetterEntity letter, Integer remaining,
                                               String message) {
        return new LetterOperationResponse(letter.getId(), letter.getStatus(), remaining,
                letter.getCoolDownEnd(), letter.getSentAt(), letter.getReadAt(),
                letter.getResolvedAt(), message);
    }

    private LetterSummaryView toSummary(Long userId, EmotionLetterEntity letter) {
        UserEntity sender = historicalUser(letter.getSenderId());
        UserEntity receiver = historicalUser(letter.getReceiverId());
        return new LetterSummaryView(letter.getId(), letter.getTitle(), letter.getEmotionTag(),
                letter.getIntensity(), letter.getStatus(), sender.getId(), historicalNickname(sender),
                receiver.getId(), historicalNickname(receiver), letter.getThreadId(),
                letter.getReplyToLetterId(), directionOf(userId, letter),
                Objects.equals(letter.getIsRead(), 1), letter.getCreatedAt(), letter.getSentAt(),
                letter.getReadAt(), letter.getResolvedAt());
    }

    private LetterDetailView toDetail(Long userId, EmotionLetterEntity letter) {
        UserEntity sender = historicalUser(letter.getSenderId());
        UserEntity receiver = historicalUser(letter.getReceiverId());
        return new LetterDetailView(letter.getId(), letter.getTitle(), letter.getContent(),
                letter.getEmotionTag(), letter.getIntensity(), letter.getStatus(), sender.getId(),
                historicalNickname(sender), receiver.getId(), historicalNickname(receiver), letter.getThreadId(),
                letter.getReplyToLetterId(),
                directionOf(userId, letter), Objects.equals(letter.getIsRead(), 1),
                letter.getReadAt(), letter.getCreatedAt(), letter.getSentAt(),
                letter.getResolvedAt(), letter.getCoolDownEnd(), List.of(), null,
                RelationshipAccess.canWrite(sender, receiver, letter));
    }

    private UserEntity historicalUser(Long userId) {
        UserEntity user = userMapper.selectIncludingDeletedById(userId);
        if (user == null) throw new NotFoundException("信件关联用户不存在");
        return user;
    }

    private String historicalNickname(UserEntity user) {
        return Integer.valueOf(1).equals(user.getIsDeleted()) ? "已注销用户" : user.getNickname();
    }

    private String directionOf(Long userId, EmotionLetterEntity letter) {
        return Objects.equals(userId, letter.getSenderId()) ? "sent" : "inbox";
    }

    private void publish(String type, Long recipientId, Long actorId, EmotionLetterEntity letter,
                         Map<String, Object> payload) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(RealtimeNotificationEvent.create(
                    type, recipientId, actorId, "LETTER", letter.getId(), payload));
        }
    }
}
