package com.couple.service.annotation.service.impl;

import com.couple.common.exception.BadRequestException;
import com.couple.common.exception.ConflictException;
import com.couple.common.exception.ForbiddenException;
import com.couple.service.annotation.dto.AnnotationCreateRequest;
import com.couple.service.annotation.dto.AnnotationReplyRequest;
import com.couple.service.annotation.entity.AnnotationEntity;
import com.couple.service.annotation.mapper.AnnotationMapper;
import com.couple.service.letter.entity.EmotionLetterEntity;
import com.couple.service.letter.mapper.EmotionLetterMapper;
import com.couple.service.letter.mapper.LetterLogMapper;
import com.couple.service.user.entity.UserEntity;
import com.couple.service.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 批注引用一致性、可见性和删除权限测试。 */
class AnnotationServiceImplTest {
    private final Map<Long, UserEntity> users = new HashMap<>();
    private EmotionLetterEntity selectedLetter;
    private AnnotationEntity selectedAnnotation;
    private List<AnnotationEntity> listedAnnotations;
    private AnnotationEntity insertedAnnotation;
    private int deleteCount;
    private int logCount;
    private long activeReplyCount;
    private AnnotationServiceImpl service;

    @BeforeEach
    void setUp() {
        users.clear();
        users.put(1L, user(1L, "甲"));
        users.put(2L, user(2L, "乙"));
        selectedLetter = letter("你好🙂世界", "READ");
        selectedAnnotation = null;
        listedAnnotations = List.of();
        insertedAnnotation = null;
        deleteCount = 0;
        logCount = 0;
        activeReplyCount = 0;

        AnnotationMapper annotationMapper = proxy(AnnotationMapper.class, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "insert" -> {
                    insertedAnnotation = (AnnotationEntity) args[0];
                    insertedAnnotation.setId(20L);
                    yield 1;
                }
                case "selectByIdForUpdate" -> selectedAnnotation;
                case "selectByLetterId" -> listedAnnotations;
                case "countActiveReplies" -> activeReplyCount;
                case "deleteById" -> {
                    deleteCount++;
                    yield 1;
                }
                default -> defaultValue(method.getReturnType());
            };
        });
        EmotionLetterMapper letterMapper = proxy(EmotionLetterMapper.class, (proxy, method, args) ->
                switch (method.getName()) {
                    case "selectById", "selectByIdForUpdate" -> selectedLetter;
                    default -> defaultValue(method.getReturnType());
                });
        LetterLogMapper logMapper = proxy(LetterLogMapper.class, (proxy, method, args) -> {
            if ("insert".equals(method.getName())) {
                logCount++;
                return 1;
            }
            return defaultValue(method.getReturnType());
        });
        UserMapper userMapper = proxy(UserMapper.class, (proxy, method, args) ->
                "selectById".equals(method.getName())
                        ? users.get((Long) args[0]) : defaultValue(method.getReturnType()));
        service = new AnnotationServiceImpl(annotationMapper, letterMapper, logMapper, userMapper);
    }

    @Test
    void createPersistsExactUtf16QuoteAndAuditLog() {
        var response = service.create(2L,
                new AnnotationCreateRequest(10L, "🙂", 2, 4, " 我理解你的感受 ", null));

        assertEquals(20L, response.annotationId());
        assertEquals("乙", response.authorNickname());
        assertEquals("🙂", insertedAnnotation.getQuoteText());
        assertEquals(2, insertedAnnotation.getQuoteStart());
        assertEquals(4, insertedAnnotation.getQuoteEnd());
        assertEquals("我理解你的感受", insertedAnnotation.getContent());
        assertEquals(1, logCount);
    }

    @Test
    void createRejectsUndeliveredLetterAndStaleQuote() {
        selectedLetter.setStatus("DRAFT");
        assertThrows(ForbiddenException.class, () -> service.create(1L,
                new AnnotationCreateRequest(10L, "你好", 0, 2, "批注", null)));

        selectedLetter.setStatus("READ");
        assertThrows(BadRequestException.class, () -> service.create(1L,
                new AnnotationCreateRequest(10L, "世界", 0, 2, "批注", null)));
    }

    @Test
    void listRequiresParticipationAndReturnsAuthorName() {
        AnnotationEntity annotation = annotation(20L, 2L, "你好", 0, 2);
        listedAnnotations = List.of(annotation);

        var response = service.list(1L, 10L);
        assertEquals(1, response.annotations().size());
        assertEquals("乙", response.annotations().get(0).authorNickname());

        assertThrows(ForbiddenException.class, () -> service.list(3L, 10L));
    }

    @Test
    void onlyAuthorCanDeleteAnnotation() {
        selectedAnnotation = annotation(20L, 2L, "你好", 0, 2);

        assertThrows(ForbiddenException.class, () -> service.delete(1L, 20L));
        var response = service.delete(2L, 20L);

        assertEquals(20L, response.annotationId());
        assertEquals(1, deleteCount);
        assertEquals(1, logCount);
    }

    @Test
    void replyInheritsRootQuoteAndCreatesSingleLevelThread() {
        selectedAnnotation = annotation(20L, 2L, "你好", 0, 2);

        var response = service.reply(1L, 20L, new AnnotationReplyRequest(" 我也想解释一下 "));

        assertEquals(20L, response.parentAnnotationId());
        assertEquals(20L, insertedAnnotation.getParentAnnotationId());
        assertEquals("你好", insertedAnnotation.getQuoteText());
        assertEquals("我也想解释一下", insertedAnnotation.getContent());
        assertEquals(1, logCount);
    }

    @Test
    void topLevelAnnotationWithRepliesCannotBeDeleted() {
        selectedAnnotation = annotation(20L, 2L, "你好", 0, 2);
        activeReplyCount = 1;

        assertThrows(ConflictException.class, () -> service.delete(2L, 20L));
        assertEquals(0, deleteCount);
    }

    private EmotionLetterEntity letter(String content, String status) {
        EmotionLetterEntity letter = new EmotionLetterEntity();
        letter.setId(10L);
        letter.setSenderId(1L);
        letter.setReceiverId(2L);
        letter.setContent(content);
        letter.setStatus(status);
        return letter;
    }

    private AnnotationEntity annotation(Long id, Long authorId, String quote, int start, int end) {
        AnnotationEntity annotation = new AnnotationEntity();
        annotation.setId(id);
        annotation.setLetterId(10L);
        annotation.setAuthorId(authorId);
        annotation.setQuoteText(quote);
        annotation.setQuoteStart(start);
        annotation.setQuoteEnd(end);
        annotation.setContent("回应");
        annotation.setCreatedAt(LocalDateTime.of(2026, 9, 13, 16, 0));
        return annotation;
    }

    private static UserEntity user(Long id, String nickname) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setNickname(nickname);
        return user;
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
