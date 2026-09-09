package com.couple.common.security;

/** 当前请求中的 JWT 身份信息。 */
public record CurrentUser(Long userId, String username) {
}
