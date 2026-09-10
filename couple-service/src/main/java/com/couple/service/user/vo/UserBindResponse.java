package com.couple.service.user.vo;

import java.time.LocalDateTime;

/** 伴侣绑定成功响应。 */
public record UserBindResponse(String message, Long partnerId, String partnerNickname,
                               String partnerRemark, LocalDateTime boundAt) {
}
