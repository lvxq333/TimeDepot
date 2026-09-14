package com.couple.service.notification.service.impl;

import com.couple.common.exception.BadRequestException;
import com.couple.common.exception.NotFoundException;
import com.couple.service.notification.RealtimeNotificationEvent;
import com.couple.service.notification.entity.UserNotificationEntity;
import com.couple.service.notification.mapper.UserNotificationMapper;
import com.couple.service.notification.service.NotificationService;
import com.couple.service.notification.vo.NotificationPageResponse;
import com.couple.service.notification.vo.NotificationReadResponse;
import com.couple.service.notification.vo.NotificationView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class NotificationServiceImpl implements NotificationService {
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() { };
    private final UserNotificationMapper mapper;
    private final ObjectMapper objectMapper;

    public NotificationServiceImpl(UserNotificationMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(RealtimeNotificationEvent event) {
        UserNotificationEntity notification = new UserNotificationEntity();
        notification.setEventId(event.eventId());
        notification.setType(event.type());
        notification.setRecipientId(event.recipientId());
        notification.setActorId(event.actorId());
        notification.setAggregateType(event.aggregateType());
        notification.setAggregateId(String.valueOf(event.aggregateId()));
        notification.setPayloadJson(writePayload(event.payload()));
        notification.setCreatedAt(LocalDateTime.now());
        mapper.insert(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPageResponse list(Long userId, int page, int pageSize) {
        if (page < 1) throw new BadRequestException("页码不能小于 1");
        if (pageSize < 1 || pageSize > 50) throw new BadRequestException("每页数量必须为 1~50");
        long offset = (long) (page - 1) * pageSize;
        var records = mapper.selectPage(userId, offset, pageSize).stream().map(this::toView).toList();
        return new NotificationPageResponse(records, mapper.countAll(userId), mapper.countUnread(userId),
                page, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return mapper.countUnread(userId);
    }

    @Override
    @Transactional
    public NotificationReadResponse markRead(Long userId, Long notificationId) {
        UserNotificationEntity notification = mapper.selectOwned(userId, notificationId);
        if (notification == null) throw new NotFoundException("通知不存在");
        int affected = notification.getReadAt() == null
                ? mapper.markRead(userId, notificationId, LocalDateTime.now()) : 0;
        return new NotificationReadResponse(mapper.countUnread(userId), affected);
    }

    @Override
    @Transactional
    public NotificationReadResponse markAllRead(Long userId) {
        int affected = mapper.markAllRead(userId, LocalDateTime.now());
        return new NotificationReadResponse(0, affected);
    }

    private NotificationView toView(UserNotificationEntity entity) {
        return new NotificationView(entity.getId(), entity.getType(), entity.getActorId(),
                entity.getAggregateType(), entity.getAggregateId(), readPayload(entity.getPayloadJson()),
                entity.getReadAt() != null, entity.getReadAt(), entity.getCreatedAt());
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("通知参数无法序列化", exception);
        }
    }

    private Map<String, Object> readPayload(String json) {
        try {
            return objectMapper.readValue(json, PAYLOAD_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }
}
