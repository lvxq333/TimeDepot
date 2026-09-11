package com.couple.service.auth.service;

import com.couple.common.security.CurrentUser;

import java.time.Duration;

/** 登录会话的创建、认证与撤销能力。 */
public interface AuthSessionService {

    String createSession(Long userId, String username, Duration ttl);

    void requireActiveSession(CurrentUser currentUser);

    void revokeCurrentSession(CurrentUser currentUser);
}
