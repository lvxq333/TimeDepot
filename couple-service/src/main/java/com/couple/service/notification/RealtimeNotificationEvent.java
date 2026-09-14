package com.couple.service.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 事务成功后投递给单个用户的轻量实时事件。 */
public record RealtimeNotificationEvent(String eventId,
                                        String type,
                                        Long recipientId,
                                        Long actorId,
                                        String aggregateType,
                                        Object aggregateId,
                                        Map<String, Object> payload,
                                        Instant occurredAt) {

    public static RealtimeNotificationEvent create(String type, Long recipientId, Long actorId,
                                                    String aggregateType, Object aggregateId,
                                                    Map<String, Object> payload) {
        return new RealtimeNotificationEvent(UUID.randomUUID().toString(), type, recipientId, actorId,
                aggregateType, aggregateId, payload == null ? Map.of() : Map.copyOf(payload), Instant.now());
    }
}
