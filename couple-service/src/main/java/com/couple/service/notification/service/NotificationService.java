package com.couple.service.notification.service;

import com.couple.service.notification.RealtimeNotificationEvent;
import com.couple.service.notification.vo.NotificationPageResponse;
import com.couple.service.notification.vo.NotificationReadResponse;

public interface NotificationService {
    void record(RealtimeNotificationEvent event);
    NotificationPageResponse list(Long userId, int page, int pageSize);
    long unreadCount(Long userId);
    NotificationReadResponse markRead(Long userId, Long notificationId);
    NotificationReadResponse markAllRead(Long userId);
}
