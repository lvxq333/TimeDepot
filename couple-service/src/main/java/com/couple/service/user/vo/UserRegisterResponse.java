package com.couple.service.user.vo;

import java.time.LocalDateTime;

/** 注册成功响应。 */
public record UserRegisterResponse(Long userId, String username, String nickname,
                                   String coupleCode, LocalDateTime createdAt) {
}
