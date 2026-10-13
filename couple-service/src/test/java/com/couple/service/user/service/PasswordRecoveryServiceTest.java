package com.couple.service.user.service;

import com.couple.common.exception.BusinessException;
import com.couple.common.exception.UnauthorizedException;
import com.couple.common.security.CurrentUser;
import com.couple.service.auth.service.AuthSessionService;
import com.couple.service.auth.service.SecurityAttemptLimiter;
import com.couple.service.user.dto.PasswordRecoveryRequest;
import com.couple.service.user.dto.RecoveryQuestionRequest;
import com.couple.service.user.dto.RecoverySetupRequest;
import com.couple.service.user.entity.RecoveryQuestionEntity;
import com.couple.service.user.entity.UserEntity;
import com.couple.service.user.mapper.RecoveryQuestionMapper;
import com.couple.service.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PasswordRecoveryServiceTest {
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UserEntity user = new UserEntity();
    private List<RecoveryQuestionEntity> storedQuestions;
    private String updatedHash;
    private Long revokedUserId;
    private long attemptCount;
    private PasswordRecoveryService service;

    @BeforeEach
    void setUp() {
        user.setId(1L);
        user.setPassword(encoder.encode("current-password"));
        storedQuestions = new ArrayList<>(List.of(question(1, "secret-one"), question(2, "secret-two")));
        attemptCount = 0;
        UserMapper users = proxy(UserMapper.class, (method, args) -> switch (method.getName()) {
            case "selectByUsernameForUpdate" -> user;
            case "selectByIdForUpdate" -> user;
            case "updatePassword" -> { updatedHash = (String) args[1]; yield 1; }
            default -> null;
        });
        RecoveryQuestionMapper questions = proxy(RecoveryQuestionMapper.class, (method, args) -> {
            if ("selectByUserId".equals(method.getName())) return storedQuestions;
            if ("insert".equals(method.getName())) {
                storedQuestions.add((RecoveryQuestionEntity) args[0]);
                return 1;
            }
            return null;
        });
        AuthSessionService sessions = new AuthSessionService() {
            public String createSession(Long userId, String username, Duration ttl) { return null; }
            public void requireActiveSession(CurrentUser currentUser) { }
            public void revokeCurrentSession(CurrentUser currentUser) { }
            public void revokeAllSessions(Long userId) { revokedUserId = userId; }
        };
        StringRedisTemplate redis = new StringRedisTemplate() {
            @SuppressWarnings("unchecked")
            @Override public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
                return (T) Long.valueOf(++attemptCount);
            }
        };
        service = new PasswordRecoveryService(users, questions, encoder, sessions,
                new SecurityAttemptLimiter(redis));
    }

    @Test
    void allAnswersRequiredBeforePasswordChangesAndSessionsRevoked() {
        assertThrows(UnauthorizedException.class, () -> service.reset(
                new PasswordRecoveryRequest("alice", List.of("secret-one", "wrong"), "new-password")));
        assertNull(updatedHash);

        service.reset(new PasswordRecoveryRequest("alice",
                List.of("secret-one", "secret-two"), "new-password"));
        assertTrue(encoder.matches("new-password", updatedHash));
        assertEquals(1L, revokedUserId);
    }

    @Test
    void tooManyAttemptsFailClosed() {
        attemptCount = 5;
        assertThrows(BusinessException.class, () -> service.reset(
                new PasswordRecoveryRequest("alice", List.of("answer"), "new-password")));
        assertNull(updatedHash);
    }

    @Test
    void existingAccountCanSetQuestionsOnlyOnceAfterPasswordCheck() {
        storedQuestions.clear();
        RecoverySetupRequest request = new RecoverySetupRequest("current-password",
                List.of(new RecoveryQuestionRequest("我的秘密提示", "private-answer")));
        assertThrows(UnauthorizedException.class, () -> service.setupForExistingAccount(1L,
                new RecoverySetupRequest("wrong-password", request.questions())));
        assertTrue(storedQuestions.isEmpty());

        service.setupForExistingAccount(1L, request);
        assertTrue(service.hasQuestions(1L));
        assertTrue(encoder.matches("private-answer", storedQuestions.getFirst().getAnswerHash()));
        assertThrows(BusinessException.class, () -> service.setupForExistingAccount(1L, request));
    }

    private RecoveryQuestionEntity question(int position, String answer) {
        RecoveryQuestionEntity question = new RecoveryQuestionEntity();
        question.setPosition(position);
        question.setAnswerHash(encoder.encode(answer));
        return question;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.function.BiFunction<java.lang.reflect.Method, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> handler.apply(method, args));
    }
}
