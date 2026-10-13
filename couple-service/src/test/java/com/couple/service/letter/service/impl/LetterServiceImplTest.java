package com.couple.service.letter.service.impl;

import com.couple.common.exception.ConflictException;
import com.couple.common.exception.BadRequestException;
import com.couple.common.exception.ForbiddenException;
import com.couple.service.letter.dto.LetterCreateRequest;
import com.couple.service.letter.entity.EmotionLetterEntity;
import com.couple.service.letter.mapper.EmotionLetterMapper;
import com.couple.service.letter.mapper.LetterLogMapper;
import com.couple.service.letter.vo.LetterOperationResponse;
import com.couple.service.user.entity.UserEntity;
import com.couple.service.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 情绪信状态机和方向权限的单元测试，不依赖 Spring 容器或 Java Agent。 */
class LetterServiceImplTest {

    private final Map<Long, UserEntity> users = new HashMap<>();
    private EmotionLetterEntity selectedLetter;
    private EmotionLetterEntity insertedLetter;
    private List<EmotionLetterEntity> visibleThread;
    private int letterInsertCount;
    private int letterUpdateCount;
    private int logInsertCount;
    private LetterServiceImpl service;

    @BeforeEach
    void setUp() {
        selectedLetter = null;
        insertedLetter = null;
        visibleThread = List.of();
        letterInsertCount = 0;
        letterUpdateCount = 0;
        logInsertCount = 0;
        users.clear();
        users.put(1L, user(1L, 2L, "甲", "BOUND"));
        users.put(2L, user(2L, 1L, "乙", "BOUND"));

        EmotionLetterMapper letterMapper = proxy(EmotionLetterMapper.class, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "insert" -> {
                    if (args[0] instanceof EmotionLetterEntity letter) {
                        insertedLetter = letter;
                        letter.setId(10L);
                        letterInsertCount++;
                        yield 1;
                    }
                    yield 0;
                }
                case "selectByIdForUpdate", "selectById" -> selectedLetter;
                case "selectVisibleThread" -> visibleThread;
                case "updateById" -> {
                    letterUpdateCount++;
                    yield 1;
                }
                case "deleteById" -> 1;
                default -> defaultValue(method.getReturnType());
            };
        });
        LetterLogMapper logMapper = proxy(LetterLogMapper.class, (proxy, method, args) -> {
            if ("insert".equals(method.getName())) {
                logInsertCount++;
                return 1;
            }
            return defaultValue(method.getReturnType());
        });
        UserMapper userMapper = proxy(UserMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName()) || "selectIncludingDeletedById".equals(method.getName()))
                return users.get((Long) args[0]);
            if ("selectPairForUpdate".equals(method.getName()))
                return List.of(users.get((Long) args[0]), users.get((Long) args[1]));
            return defaultValue(method.getReturnType());
        });
        var visibilityMapper = proxy(com.couple.service.letter.mapper.LetterVisibilityMapper.class,
                (proxy, method, args) -> "hide".equals(method.getName()) ? 1 : 0);
        service = new LetterServiceImpl(letterMapper, logMapper, userMapper, visibilityMapper, 300);
    }

    @Test
    void createUsesCurrentPartnerAsReceiverAndWritesAuditLog() {
        users.put(1L, user(1L, 2L, "甲", "BOUND"));
        users.put(2L, user(2L, 1L, "乙", "BOUND"));

        LetterOperationResponse response = service.create(
                1L, new LetterCreateRequest("  标题  ", "  内容  ", "sad", null, null));

        assertEquals(10L, response.letterId());
        assertEquals("DRAFT", response.status());
        assertEquals(1, letterInsertCount);
        assertEquals(2L, insertedLetter.getReceiverId());
        assertEquals("标题", insertedLetter.getTitle());
        assertEquals(5, insertedLetter.getIntensity());
        assertEquals(1, logInsertCount);
    }

    @Test
    void unboundUserCannotCreateLetter() {
        users.put(1L, user(1L, null, "甲", "UNBOUND"));

        assertThrows(ConflictException.class, () -> service.create(
                1L, new LetterCreateRequest(null, "内容", "sad", 5, null)));
        assertEquals(0, letterInsertCount);
    }

    @Test
    void replyInheritsThreadAndTargetsOriginalSender() {
        users.put(1L, user(1L, 2L, "甲", "BOUND"));
        users.put(2L, user(2L, 1L, "乙", "BOUND"));
        selectedLetter = letter(10L, 2L, 1L, "READ");
        selectedLetter.setThreadId("thread-1");
        selectedLetter.setIsRead(1);

        LetterOperationResponse response = service.create(
                1L, new LetterCreateRequest("回复", "回应内容", "suggest", 5, 10L));

        assertEquals("DRAFT", response.status());
        assertEquals("thread-1", insertedLetter.getThreadId());
        assertEquals(10L, insertedLetter.getReplyToLetterId());
        assertEquals(2L, insertedLetter.getReceiverId());
    }

    @Test
    void threadReturnsVisibleLettersInTimelineOrder() {
        users.put(1L, user(1L, 2L, "甲", "BOUND"));
        users.put(2L, user(2L, 1L, "乙", "BOUND"));
        selectedLetter = letter(10L, 1L, 2L, "READ");
        selectedLetter.setThreadId("thread-1");
        selectedLetter.setIsRead(1);
        EmotionLetterEntity reply = letter(11L, 2L, 1L, "SENT");
        reply.setThreadId("thread-1");
        reply.setReplyToLetterId(10L);
        visibleThread = List.of(selectedLetter, reply);

        var response = service.thread(1L, 10L);

        assertEquals("thread-1", response.threadId());
        assertEquals(2, response.letters().size());
        assertEquals(11L, response.letters().get(1).letterId());
        assertEquals(10L, response.letters().get(1).replyToLetterId());
    }

    @Test
    void survivingPartnerCanReadLetterAfterSenderCancels() {
        UserEntity cancelled = users.get(1L);
        cancelled.setIsDeleted(1);
        cancelled.setNickname(null);
        cancelled.setPartnerId(null);
        cancelled.setRelationshipStatus("UNBOUND");
        UserEntity survivor = users.get(2L);
        survivor.setPartnerId(null);
        survivor.setRelationshipStatus("UNBOUND_HISTORY");
        selectedLetter = letter(10L, 1L, 2L, "READ");

        var detail = service.detail(2L, 10L);

        assertEquals("已注销用户", detail.senderNickname());
        assertEquals("乙", detail.receiverNickname());
        assertTrue(!detail.editable());
    }

    @Test
    void submitPersistsCooldownAndEarlySendIsRejected() {
        selectedLetter = letter(10L, 1L, 2L, "DRAFT");

        LetterOperationResponse response = service.submit(1L, 10L);

        assertEquals("COOLDOWN", response.status());
        assertEquals(300, response.cooldownRemaining());
        assertNotNull(response.cooldownEndAt());
        ConflictException exception = assertThrows(ConflictException.class,
                () -> service.send(1L, 10L));
        assertTrue(exception.getMessage().contains("冷静期还剩"));
    }

    @Test
    void oldCooldownLetterCannotBeSentAfterUnbind() {
        selectedLetter = letter(10L, 1L, 2L, "COOLDOWN");
        selectedLetter.setCoolDownEnd(LocalDateTime.now().minusSeconds(1));
        users.put(1L, user(1L, null, "甲", "UNBOUND_HISTORY"));
        users.put(2L, user(2L, null, "乙", "UNBOUND_HISTORY"));

        assertThrows(ConflictException.class, () -> service.send(1L, 10L));
        assertEquals(0, letterUpdateCount);
    }

    @Test
    void historyIsReadableButCannotBeChangedAfterUnbind() {
        selectedLetter = letter(10L, 1L, 2L, "SENT");
        users.put(1L, user(1L, null, "甲", "UNBOUND_HISTORY"));
        users.put(2L, user(2L, null, "乙", "UNBOUND_HISTORY"));

        assertEquals(false, service.detail(2L, 10L).editable());
        assertEquals("SENT", service.read(2L, 10L).status());
        assertEquals(0, letterUpdateCount);
        assertThrows(ConflictException.class, () -> service.resolve(2L, 10L));
    }

    @Test
    void rebindingSamePartnerRestoresAllowedLetterOperations() {
        selectedLetter = letter(10L, 1L, 2L, "DRAFT");
        LocalDateTime unboundAt = selectedLetter.getCreatedAt().plusMinutes(1);
        users.get(1L).setUnboundAt(unboundAt);
        users.get(2L).setUnboundAt(unboundAt);

        assertEquals(true, service.detail(1L, 10L).editable());
        assertEquals("COOLDOWN", service.submit(1L, 10L).status());
    }

    @Test
    void bindingOtherPartnersKeepsOriginalLetterReadOnly() {
        selectedLetter = letter(10L, 1L, 2L, "DRAFT");
        users.get(1L).setPartnerId(3L);
        users.get(2L).setPartnerId(4L);

        assertEquals(false, service.detail(1L, 10L).editable());
        assertThrows(ConflictException.class, () -> service.submit(1L, 10L));
        assertEquals(0, letterUpdateCount);
    }

    @Test
    void oldCooldownLetterCanBeSentAfterRebindingSamePartner() {
        selectedLetter = letter(10L, 1L, 2L, "COOLDOWN");
        selectedLetter.setThreadId("thread-1");
        selectedLetter.setCoolDownEnd(LocalDateTime.now().minusSeconds(1));
        LocalDateTime unboundAt = selectedLetter.getCreatedAt().plusMinutes(1);
        users.get(1L).setUnboundAt(unboundAt);
        users.get(2L).setUnboundAt(unboundAt);

        assertEquals("SENT", service.send(1L, 10L).status());
        assertEquals(1, letterUpdateCount);
    }

    @Test
    void onlyReceiverCanMarkReadAndRepeatedReadIsIdempotent() {
        selectedLetter = letter(10L, 1L, 2L, "SENT");

        assertThrows(ForbiddenException.class, () -> service.read(1L, 10L));
        LetterOperationResponse first = service.read(2L, 10L);
        LetterOperationResponse second = service.read(2L, 10L);

        assertEquals("READ", first.status());
        assertEquals("READ", second.status());
        assertNotNull(first.readAt());
        assertEquals(1, selectedLetter.getIsRead());
        assertEquals(1, letterUpdateCount);
        assertEquals(1, logInsertCount);
    }

    @Test
    void receiverCannotOpenDraftButSenderCan() {
        selectedLetter = letter(10L, 1L, 2L, "DRAFT");
        users.put(1L, user(1L, 2L, "甲", "BOUND"));
        users.put(2L, user(2L, 1L, "乙", "BOUND"));

        assertThrows(ForbiddenException.class, () -> service.detail(2L, 10L));
        assertEquals("sent", service.detail(1L, 10L).direction());
    }

    @Test
    void onlyReceiverCanResolveReadLetter() {
        selectedLetter = letter(10L, 1L, 2L, "READ");
        selectedLetter.setIsRead(1);

        assertThrows(ForbiddenException.class, () -> service.resolve(1L, 10L));
        LetterOperationResponse response = service.resolve(2L, 10L);

        assertEquals("RESOLVED", response.status());
        assertNotNull(response.resolvedAt());
    }

    @Test
    void threadSearchRejectsInvalidDateRangeAndLongKeyword() {
        users.put(1L, user(1L, 2L, "甲", "BOUND"));

        assertThrows(BadRequestException.class, () -> service.listThreads(1L, 1, 20,
                null, null, null, null,
                LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 21)));
        assertThrows(BadRequestException.class, () -> service.listThreads(1L, 1, 20,
                null, null, null, "x".repeat(101), null, null));
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

    private static UserEntity user(Long id, Long partnerId, String nickname, String relationshipStatus) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setPartnerId(partnerId);
        user.setNickname(nickname);
        user.setRelationshipStatus(relationshipStatus);
        return user;
    }

    private static EmotionLetterEntity letter(Long id, Long senderId, Long receiverId, String status) {
        EmotionLetterEntity letter = new EmotionLetterEntity();
        letter.setId(id);
        letter.setSenderId(senderId);
        letter.setReceiverId(receiverId);
        letter.setTitle("标题");
        letter.setContent("内容");
        letter.setEmotionTag("sad");
        letter.setIntensity(5);
        letter.setStatus(status);
        letter.setIsRead(0);
        letter.setIsDeleted(0);
        letter.setCreatedAt(LocalDateTime.now());
        letter.setUpdatedAt(LocalDateTime.now());
        return letter;
    }
}
