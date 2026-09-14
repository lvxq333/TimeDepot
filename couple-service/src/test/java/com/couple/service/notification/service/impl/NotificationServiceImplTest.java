package com.couple.service.notification.service.impl;

import com.couple.common.exception.NotFoundException;
import com.couple.service.notification.RealtimeNotificationEvent;
import com.couple.service.notification.entity.UserNotificationEntity;
import com.couple.service.notification.mapper.UserNotificationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationServiceImplTest {

    @Test
    void recordsEventWithoutCopyingPrivateContent() {
        AtomicReference<UserNotificationEntity> inserted = new AtomicReference<>();
        UserNotificationMapper mapper = mapper((method, args) -> {
            if ("insert".equals(method)) { inserted.set((UserNotificationEntity) args[0]); return 1; }
            return defaultValue(method);
        });
        var service = new NotificationServiceImpl(mapper, new ObjectMapper());
        var event = RealtimeNotificationEvent.create("LETTER_SENT", 2L, 1L,
                "LETTER", 9L, Map.of("title", "好好聊聊"));

        service.record(event);

        assertEquals(event.eventId(), inserted.get().getEventId());
        assertEquals(2L, inserted.get().getRecipientId());
        assertEquals("{\"title\":\"好好聊聊\"}", inserted.get().getPayloadJson());
    }

    @Test
    void listsOnlyRecipientNotificationsWithUnreadCount() {
        UserNotificationEntity entity = notification(8L, 2L, null);
        UserNotificationMapper mapper = mapper((method, args) -> switch (method) {
            case "selectPage" -> List.of(entity);
            case "countAll" -> 3L;
            case "countUnread" -> 2L;
            default -> defaultValue(method);
        });
        var result = new NotificationServiceImpl(mapper, new ObjectMapper()).list(2L, 1, 20);

        assertEquals(3, result.total());
        assertEquals(2, result.unreadCount());
        assertEquals(8L, result.records().getFirst().notificationId());
        assertTrue(!result.records().getFirst().read());
    }

    @Test
    void cannotMarkAnotherUsersNotificationAsRead() {
        UserNotificationMapper mapper = mapper((method, args) ->
                "selectOwned".equals(method) ? null : defaultValue(method));
        var service = new NotificationServiceImpl(mapper, new ObjectMapper());
        assertThrows(NotFoundException.class, () -> service.markRead(2L, 99L));
    }

    private UserNotificationEntity notification(Long id, Long recipientId, LocalDateTime readAt) {
        UserNotificationEntity entity = new UserNotificationEntity();
        entity.setId(id);
        entity.setEventId("event-1");
        entity.setType("LETTER_SENT");
        entity.setRecipientId(recipientId);
        entity.setActorId(1L);
        entity.setAggregateType("LETTER");
        entity.setAggregateId("9");
        entity.setPayloadJson("{\"title\":\"标题\"}");
        entity.setReadAt(readAt);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    private UserNotificationMapper mapper(Invocation invocation) {
        return (UserNotificationMapper) Proxy.newProxyInstance(UserNotificationMapper.class.getClassLoader(),
                new Class<?>[]{UserNotificationMapper.class},
                (proxy, method, args) -> invocation.invoke(method.getName(), args));
    }

    private Object defaultValue(String method) {
        if (method.startsWith("count")) return 0L;
        if (method.startsWith("mark") || "insert".equals(method)) return 0;
        return null;
    }

    private interface Invocation { Object invoke(String method, Object[] args) throws Throwable; }
}
