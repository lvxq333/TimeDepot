package com.couple.service.auth.service;

import com.couple.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityAttemptLimiterTest {
    /** 超过窗口上限返回 429，且键名不包含明文用户名。 */
    @Test
    void limitsAttemptsAndHashesSubject() {
        Map<String, Long> counts = new HashMap<>();
        StringRedisTemplate redis = new StringRedisTemplate() {
            @SuppressWarnings("unchecked")
            @Override public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
                String key = keys.getFirst();
                if (key.contains("alice")) throw new AssertionError("键名泄漏用户名");
                return (T) counts.merge(key, 1L, Long::sum);
            }
        };
        SecurityAttemptLimiter limiter = new SecurityAttemptLimiter(redis);
        limiter.check("login", "alice", 2, Duration.ofMinutes(15));
        limiter.check("login", "alice", 2, Duration.ofMinutes(15));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> limiter.check("login", "alice", 2, Duration.ofMinutes(15)));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
    }

    /** Redis 故障时拒绝敏感操作，避免绕过限制。 */
    @Test
    void failsClosedWhenRedisIsUnavailable() {
        StringRedisTemplate redis = new StringRedisTemplate() {
            @Override public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
                throw new DataAccessResourceFailureException("redis down");
            }
        };
        BusinessException exception = assertThrows(BusinessException.class,
                () -> new SecurityAttemptLimiter(redis).check("login", "alice", 2, Duration.ofMinutes(15)));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
    }
}
