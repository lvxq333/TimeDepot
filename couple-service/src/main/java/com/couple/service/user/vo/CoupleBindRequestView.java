package com.couple.service.user.vo;

import java.time.LocalDateTime;

/** 绑定申请展示信息，包含双方公开账号信息和处理状态。 */
public record CoupleBindRequestView(Long requestId,
                                    Long requesterId,
                                    String requesterUsername,
                                    String requesterNickname,
                                    Long receiverId,
                                    String receiverUsername,
                                    String receiverNickname,
                                    String status,
                                    LocalDateTime createdAt,
                                    LocalDateTime processedAt) {
}
