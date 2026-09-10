package com.couple.service.user.vo;

import java.time.LocalDateTime;

/** 会员状态响应。 */
public record MembershipView(String plan, String status, LocalDateTime expireAt) {
}
