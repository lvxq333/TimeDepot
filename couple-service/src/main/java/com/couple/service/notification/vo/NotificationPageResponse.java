package com.couple.service.notification.vo;

import java.util.List;

public record NotificationPageResponse(List<NotificationView> records, long total,
                                       long unreadCount, int page, int pageSize) { }
