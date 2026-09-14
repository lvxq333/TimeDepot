package com.couple.service.notification.vo;

import java.time.LocalDateTime;
import java.util.Map;

public record NotificationView(Long notificationId, String type, Long actorId,
                               String aggregateType, String aggregateId,
                               Map<String, Object> payload, boolean read,
                               LocalDateTime readAt, LocalDateTime createdAt) { }
