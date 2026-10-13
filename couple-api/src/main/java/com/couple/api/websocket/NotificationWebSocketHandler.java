package com.couple.api.websocket;

import com.couple.common.security.CurrentUser;
import com.couple.common.exception.UnauthorizedException;
import com.couple.service.auth.service.AuthSessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 维护用户的全部在线连接，并为业务通知提供按用户投递能力。 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final AuthSessionService authSessionService;
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    /** 注入会话服务，用于连接建立后的持续校验。 */
    public NotificationWebSocketHandler(ObjectMapper objectMapper, AuthSessionService authSessionService) {
        this.objectMapper = objectMapper;
        this.authSessionService = authSessionService;
    }

    /** 保存新连接，并发送连接确认。 */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        CurrentUser currentUser = currentUser(session);
        userSessions.computeIfAbsent(currentUser.userId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(session);
        session.sendMessage(new TextMessage("{\"type\":\"CONNECTED\"}"));
    }

    /** 每次心跳先复查会话，退出登录后的连接不再响应。 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (!requireActive(session)) return;
        if ("PING".equalsIgnoreCase(message.getPayload())) {
            session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        remove(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    /** 向指定用户的全部活动连接广播一条 JSON 消息。 */
    public void sendToUser(Long userId, Object payload) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) return;
        final String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("实时通知无法序列化", exception);
        }
        TextMessage message = new TextMessage(json);
        sessions.forEach(session -> send(session, message));
    }

    public int connectionCount(Long userId) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        return sessions == null ? 0 : sessions.size();
    }

    /** 推送前再次核对会话，防止已撤销连接收到新的通知。 */
    private void send(WebSocketSession session, TextMessage message) {
        if (!session.isOpen()) {
            remove(session);
            return;
        }
        if (!requireActive(session)) return;
        try {
            synchronized (session) {
                session.sendMessage(message);
            }
        } catch (IOException exception) {
            remove(session);
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException ignored) {
                // 连接已经不可用，无需再次处理。
            }
        }
    }

    /** 已建立的连接也持续检查登录会话；退出后在下次心跳或推送时关闭。 */
    private boolean requireActive(WebSocketSession session) {
        try {
            authSessionService.requireActiveSession(currentUser(session));
            return true;
        } catch (UnauthorizedException exception) {
            remove(session);
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException ignored) {
                // 连接已经断开，无需再次关闭。
            }
            return false;
        }
    }

    private CurrentUser currentUser(WebSocketSession session) {
        Object value = session.getAttributes().get(WebSocketAuthHandshakeInterceptor.CURRENT_USER_ATTRIBUTE);
        if (value instanceof CurrentUser currentUser) return currentUser;
        throw new IllegalStateException("WebSocket 连接缺少登录用户");
    }

    private void remove(WebSocketSession session) {
        Object value = session.getAttributes().get(WebSocketAuthHandshakeInterceptor.CURRENT_USER_ATTRIBUTE);
        if (!(value instanceof CurrentUser currentUser)) return;
        userSessions.computeIfPresent(currentUser.userId(), (ignored, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
    }
}
