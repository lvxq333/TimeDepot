package com.couple.api.controller;

import com.couple.common.response.ApiResponse;
import com.couple.common.security.UserContext;
import com.couple.service.notification.service.NotificationService;
import com.couple.service.notification.vo.NotificationPageResponse;
import com.couple.service.notification.vo.NotificationReadResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<NotificationPageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(notificationService.list(UserContext.requireUserId(), page, pageSize));
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationReadResponse> unreadCount() {
        return ApiResponse.success(new NotificationReadResponse(
                notificationService.unreadCount(UserContext.requireUserId()), 0));
    }

    @PutMapping("/{notificationId}/read")
    public ApiResponse<NotificationReadResponse> markRead(@PathVariable Long notificationId) {
        return ApiResponse.success(notificationService.markRead(UserContext.requireUserId(), notificationId));
    }

    @PutMapping("/read-all")
    public ApiResponse<NotificationReadResponse> markAllRead() {
        return ApiResponse.success(notificationService.markAllRead(UserContext.requireUserId()));
    }
}
