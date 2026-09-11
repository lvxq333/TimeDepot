package com.couple.service.letter.vo;

import java.time.LocalDateTime;

/** 情绪信列表项，direction 表示当前用户在这封信中的方向。 */
public record LetterSummaryView(Long letterId,
                                String title,
                                String emotionTag,
                                int intensity,
                                String status,
                                Long senderId,
                                String senderNickname,
                                Long receiverId,
                                String receiverNickname,
                                String threadId,
                                Long replyToLetterId,
                                String direction,
                                boolean isRead,
                                LocalDateTime createdAt,
                                LocalDateTime sentAt,
                                LocalDateTime readAt) {
}
