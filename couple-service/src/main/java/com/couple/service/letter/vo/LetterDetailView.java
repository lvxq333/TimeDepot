package com.couple.service.letter.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 情绪信详情；尚未开发的批注和共识字段返回空值以保持接口兼容。 */
public record LetterDetailView(Long letterId,
                               String title,
                               String content,
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
                               LocalDateTime readAt,
                               LocalDateTime createdAt,
                               LocalDateTime sentAt,
                               LocalDateTime resolvedAt,
                               LocalDateTime cooldownEndAt,
                               List<Object> annotations,
                               Object consensusMemo,
                               boolean editable) {
}
