package com.couple.common.security;

import java.time.Instant;

/** 当前请求中的 JWT 身份和会话信息。 */
public record CurrentUser(Long userId, String username, String sessionId, Instant expiresAt) {
}
