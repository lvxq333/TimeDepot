package com.couple.common.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** JWT 会话声明的单元测试。 */
class JwtTokenUtilTest {

    private static final String SECRET = "12345678901234567890123456789012";

    @Test
    void tokenCarriesSessionIdentity() {
        String token = JwtTokenUtil.createToken(
                SECRET, 7L, "alice", "session-123", Duration.ofMinutes(5));

        CurrentUser user = JwtTokenUtil.parse(SECRET, token);

        assertEquals(7L, user.userId());
        assertEquals("alice", user.username());
        assertEquals("session-123", user.sessionId());
    }
}
