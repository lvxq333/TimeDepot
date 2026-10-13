package com.couple.api.websocket;

import com.couple.common.exception.UnauthorizedException;
import com.couple.common.security.CurrentUser;
import com.couple.service.auth.service.AuthSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSocketTicketServiceTest {
    /** 同一票据只能握手一次；退出登录后即使票据未过期也不能使用。 */
    @Test
    void ticketIsSingleUseAndRequiresActiveSession() {
        Map<String, String> stored = new HashMap<>();
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(), new Class<?>[]{ValueOperations.class},
                (proxy, method, args) -> {
                    if ("set".equals(method.getName())) stored.put((String) args[0], (String) args[1]);
                    return null;
                });
        StringRedisTemplate redis = new StringRedisTemplate() {
            @Override public ValueOperations<String, String> opsForValue() { return values; }
            @SuppressWarnings("unchecked")
            @Override public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
                return (T) stored.remove(keys.getFirst());
            }
        };
        boolean[] revoked = {false};
        AuthSessionService sessions = new AuthSessionService() {
            public String createSession(Long userId, String username, Duration ttl) { return null; }
            public void requireActiveSession(CurrentUser user) {
                if (revoked[0]) throw new UnauthorizedException("登录会话已失效");
            }
            public void revokeCurrentSession(CurrentUser user) { revoked[0] = true; }
            public void revokeAllSessions(Long userId) { revoked[0] = true; }
        };
        WebSocketTicketService service = new WebSocketTicketService(redis, sessions);
        CurrentUser user = new CurrentUser(7L, "alice", "session-1", Instant.now().plusSeconds(60));

        String first = service.issue(user);
        assertEquals(7L, service.consume(first).userId());
        assertThrows(UnauthorizedException.class, () -> service.consume(first));

        String second = service.issue(user);
        sessions.revokeCurrentSession(user);
        assertThrows(UnauthorizedException.class, () -> service.consume(second));
    }
}
