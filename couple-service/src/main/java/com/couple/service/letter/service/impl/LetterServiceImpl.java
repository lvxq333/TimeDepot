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
import com.couple.service.letter.mapper.EmotionLetterMapper;
import com.couple.service.letter.mapper.LetterLogMapper;
import com.couple.service.letter.service.LetterService;
import com.couple.service.letter.vo.LetterDetailView;
import com.couple.service.letter.vo.LetterOperationResponse;
import com.couple.service.letter.vo.LetterPageResponse;
import com.couple.service.letter.vo.LetterSummaryView;
import com.couple.service.letter.vo.LetterThreadView;
import com.couple.service.notification.RealtimeNotificationEvent;
import com.couple.service.user.entity.UserEntity;
import com.couple.service.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
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

    private final EmotionLetterMapper letterMapper;
    private final LetterLogMapper logMapper;
    private final UserMapper userMapper;
    private final long cooldownSeconds;
    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    public LetterServiceImpl(EmotionLetterMapper letterMapper,
                             LetterLogMapper logMapper,
                             UserMapper userMapper,
                             @Value("${letter.cooldown-seconds:300}") long cooldownSeconds) {
        this.letterMapper = letterMapper;
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
        EmotionLetterEntity parent = null;
        if (request.replyToLetterId() != null) {
            parent = requireLockedLetter(request.replyToLetterId());
            requireReplyTarget(userId, parent, receiver);
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
        writeLog(letter.getId(), userId, "CREATE", "创建情绪信草稿", now);
        return operation(letter, null, "已保存为草稿");
    }

    /** 仅允许发送方修改草稿，发送后的正文永久保持不变。 */
    @Override
    @Transactional
    public LetterOperationResponse update(Long userId, Long letterId, LetterUpdateRequest request) {
        EmotionLetterEntity letter = requireLockedLetter(letterId);
        requireSender(userId, letter);
        requireStatus(letter, DRAFT, "只有草稿可以修改");
        applyContent(letter, request.title(), request.content(), request.emotionTag(), request.intensity());
        letter.setUpdatedAt(LocalDateTime.now());
        updateOrFail(letter);
        writeLog(letterId, userId, "UPDATE", "修改情绪信草稿", letter.getUpdatedAt());
        return operation(letter, null, "草稿已更新");
    }

    /** 将草稿提交到固定冷静期，结束时间持久化到数据库。 */
    @Override
    @Transactional
    public LetterOperationResponse submit(Long userId, Long letterId) {
        EmotionLetterEntity letter = requireLockedLetter(letterId);
        requireSender(userId, letter);
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
        LocalDateTime now = LocalDateTime.now();
        letter.setStatus(SENT);
        letter.setCoolDownEnd(null);
        letter.setSentAt(now);
        letter.setUpdatedAt(now);
        updateOrFail(letter);
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

    /** 只有发送方或已经实际收到信件的接收方可以查看详情。 */
    @Override
    @Transactional(readOnly = true)
    public LetterDetailView detail(Long userId, Long letterId) {
        EmotionLetterEntity letter = letterMapper.selectById(letterId);
        if (letter == null) throw new NotFoundException("情绪信不存在");
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
        return new LetterThreadView(anchor.getThreadId(), title, views);
    }

    /** 接收方首次打开信件时标记已读；重复调用保持幂等。 */
    @Override
    @Transactional
    public LetterOperationResponse read(Long userId, Long letterId) {
        EmotionLetterEntity letter = requireLockedLetter(letterId);
        requireReceiver(userId, letter);
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
        if (RESOLVED.equals(letter.getStatus())) {
            return operation(letter, null, "信件已标记为已解决");
        }
        requireStatus(letter, READ, "只有已读信件可以标记为已解决");
        LocalDateTime now = LocalDateTime.now();
        letter.setStatus(RESOLVED);
        letter.setResolvedAt(now);
        letter.setUpdatedAt(now);
        updateOrFail(letter);
        writeLog(letterId, userId, "RESOLVE", "接收方确认情绪问题已解决", now);
        publish("LETTER_RESOLVED", letter.getSenderId(), userId, letter, Map.of());

        // TODO(LLM): 后期可温和建议发起线程解决申请，但必须由双方在备忘录流程中确认。
        return operation(letter, null, "已将这封信标记为已解决");
    }

    /** 发送方只能逻辑删除自己的草稿。 */
    @Override
    @Transactional
    public LetterOperationResponse delete(Long userId, Long letterId) {
        EmotionLetterEntity letter = requireLockedLetter(letterId);
        requireSender(userId, letter);
        requireStatus(letter, DRAFT, "只有草稿可以删除");
        LocalDateTime now = LocalDateTime.now();
        writeLog(letterId, userId, "DELETE", "删除情绪信草稿", now);
        if (letterMapper.deleteById(letterId) != 1) {
            throw new ConflictException("草稿状态已发生变化，请刷新后重试");
        }
        return new LetterOperationResponse(letterId, DRAFT, null, null,
                null, null, null, "删除成功");
    }

    private EmotionLetterEntity requireLockedLetter(Long letterId) {
        EmotionLetterEntity letter = letterMapper.selectByIdForUpdate(letterId);
        if (letter == null) throw new NotFoundException("情绪信不存在");
        return letter;
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
        UserEntity sender = requireUser(letter.getSenderId());
        UserEntity receiver = requireUser(letter.getReceiverId());
        return new LetterSummaryView(letter.getId(), letter.getTitle(), letter.getEmotionTag(),
                letter.getIntensity(), letter.getStatus(), sender.getId(), sender.getNickname(),
                receiver.getId(), receiver.getNickname(), letter.getThreadId(),
                letter.getReplyToLetterId(), directionOf(userId, letter),
                Objects.equals(letter.getIsRead(), 1), letter.getCreatedAt(), letter.getSentAt(),
                letter.getReadAt());
    }

    private LetterDetailView toDetail(Long userId, EmotionLetterEntity letter) {
        UserEntity sender = requireUser(letter.getSenderId());
        UserEntity receiver = requireUser(letter.getReceiverId());
        return new LetterDetailView(letter.getId(), letter.getTitle(), letter.getContent(),
                letter.getEmotionTag(), letter.getIntensity(), letter.getStatus(), sender.getId(),
                sender.getNickname(), receiver.getId(), receiver.getNickname(), letter.getThreadId(),
                letter.getReplyToLetterId(),
                directionOf(userId, letter), Objects.equals(letter.getIsRead(), 1),
                letter.getReadAt(), letter.getCreatedAt(), letter.getSentAt(),
                letter.getResolvedAt(), letter.getCoolDownEnd(), List.of(), null);
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
