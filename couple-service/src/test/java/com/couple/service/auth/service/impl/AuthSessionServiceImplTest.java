package com.couple.service.auth.service.impl;

import com.couple.common.exception.UnauthorizedException;
import com.couple.common.security.CurrentUser;
import com.couple.service.auth.entity.AuthSessionEntity;
import com.couple.service.auth.mapper.AuthSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** MySQL 会话持久化与 Redis 降级路径的单元测试。 */
class AuthSessionServiceImplTest {

    private SessionMapperStub mapperStub;
    private AuthSessionMapper mapper;
    private AuthSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        mapperStub = new SessionMapperStub();
        mapper = (AuthSessionMapper) Proxy.newProxyInstance(
                AuthSessionMapper.class.getClassLoader(),
                new Class<?>[]{AuthSessionMapper.class}, mapperStub);
        service = createService(new StringRedisTemplate());
    }

    @Test
    void createSessionPersistsAuthoritativeRecord() {
        String sessionId = service.createSession(1L, "alice", Duration.ofDays(7));

        assertNotNull(sessionId);
        assertNotNull(mapperStub.inserted);
    }

    @Test
    void redisFailureFallsBackToMysql() {
        StringRedisTemplate unavailableRedis = new StringRedisTemplate() {
            @Override
            public Boolean hasKey(String key) {
                throw new RedisConnectionFailureException("redis unavailable");
            }
        };
        service = createService(unavailableRedis);
        mapperStub.selected = activeSession();

        assertDoesNotThrow(() -> service.requireActiveSession(currentUser()));
    }

    @Test
    void revokedMarkerRejectsWithoutDatabaseQuery() {
        StringRedisTemplate revokedRedis = new StringRedisTemplate() {
            @Override
            public Boolean hasKey(String key) {
                return key.equals("couple:auth:revoked:session-1");
            }
        };
        service = createService(revokedRedis);

        assertThrows(UnauthorizedException.class,
                () -> service.requireActiveSession(currentUser()));

        if (mapperStub.selectCount != 0) {
            throw new AssertionError("命中撤销标记后不应查询 MySQL");
        }
    }

    private AuthSessionServiceImpl createService(StringRedisTemplate redisTemplate) {
        return new AuthSessionServiceImpl(mapper, redisTemplate, 1800, 45, 120, 3);
    }

    private CurrentUser currentUser() {
        return new CurrentUser(
                1L, "alice", "session-1", Instant.now().plus(Duration.ofHours(1)));
    }

    private AuthSessionEntity activeSession() {
        AuthSessionEntity session = new AuthSessionEntity();
        session.setSessionId("session-1");
        session.setUserId(1L);
        session.setUsername("alice");
        session.setStatus("ACTIVE");
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        return session;
    }

    private static final class SessionMapperStub implements InvocationHandler {
        private AuthSessionEntity inserted;
        private AuthSessionEntity selected;
        private int selectCount;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "insert" -> {
                    inserted = (AuthSessionEntity) args[0];
                    yield 1;
                }
                case "selectBySessionId" -> {
                    selectCount++;
                    yield selected;
                }
                case "revoke" -> 1;
                case "toString" -> "SessionMapperStub";
                default -> defaultValue(method.getReturnType());
            };
        }

        private Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == long.class) return 0L;
            if (type == double.class) return 0D;
            if (type == float.class) return 0F;
            if (type == char.class) return '\0';
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            return 0;
        }
    }
}
