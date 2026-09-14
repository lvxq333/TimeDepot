package com.couple.api.websocket;

import com.couple.service.notification.RealtimeNotificationEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 只在业务事务提交成功后发送，避免客户端收到最终已回滚的状态。 */
@Component
public class RealtimeNotificationEventListener {
    private final NotificationWebSocketHandler handler;

    public RealtimeNotificationEventListener(NotificationWebSocketHandler handler) {
        this.handler = handler;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotification(RealtimeNotificationEvent event) {
        handler.sendToUser(event.recipientId(), event);
    }
}
