package com.couple.service.user.vo;

import java.time.LocalDateTime;

/** 伴侣备注修改成功响应。 */
public record PartnerRemarkResponse(Long partnerId, String partnerNickname,
                                    String partnerRemark, LocalDateTime updatedAt) {
}
