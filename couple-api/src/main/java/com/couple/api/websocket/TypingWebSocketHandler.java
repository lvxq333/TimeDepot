package com.couple.api.websocket;

import com.couple.common.security.CurrentUser;
import com.couple.service.letter.entity.EmotionLetterEntity;
import com.couple.service.letter.mapper.EmotionLetterMapper;
import com.couple.service.user.entity.UserEntity;
import com.couple.service.user.mapper.UserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 校验信件参与关系，并把输入状态只转发给当前话题的另一方。 */
@Component
public class TypingWebSocketHandler extends TextWebSocketHandler {

    private static final Set<String> SHARED_STATUSES = Set.of("SENT", "READ", "RESOLVED");

    private final ObjectMapper objectMapper;
    private final EmotionLetterMapper letterMapper;
    private final UserMapper userMapper;
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ActiveTyping> activeTyping = new ConcurrentHashMap<>();

    public TypingWebSocketHandler(ObjectMapper objectMapper,
                                  EmotionLetterMapper letterMapper,
                                  UserMapper userMapper) {
        this.objectMapper = objectMapper;
        this.letterMapper = letterMapper;
        this.userMapper = userMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        CurrentUser currentUser = currentUser(session);
        userSessions.computeIfAbsent(currentUser.userId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(session);
        send(session, Map.of("type", "CONNECTED"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if ("PING".equalsIgnoreCase(message.getPayload())) {
            send(session, Map.of("type", "PONG"));
            return;
        }
        try {
            TypingRequest request = objectMapper.readValue(message.getPayload(), TypingRequest.class);
            if (!"typing".equals(request.type()) || request.letterId() == null || request.typing() == null) {
                sendError(session, "输入状态消息格式不正确");
                return;
            }
            CurrentUser currentUser = currentUser(session);
            TypingRoute route = resolveRoute(currentUser, request.letterId());
            TypingEvent event = new TypingEvent("typing", currentUser.userId(), route.senderNickname(),
                    request.letterId(), request.typing());
            broadcast(route.recipientId(), event);
            if (request.typing()) {
                activeTyping.put(session.getId(), new ActiveTyping(request.letterId(), route));
            } else {
                activeTyping.remove(session.getId());
            }
        } catch (JsonProcessingException exception) {
            sendError(session, "输入状态消息不是有效 JSON");
        } catch (IllegalArgumentException exception) {
            sendError(session, exception.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        stopActiveTyping(session);
        remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        stopActiveTyping(session);
        remove(session);
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }

    public int connectionCount(Long userId) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        return sessions == null ? 0 : sessions.size();
    }

    private TypingRoute resolveRoute(CurrentUser currentUser, Long letterId) {
        EmotionLetterEntity letter = letterMapper.selectById(letterId);
        if (letter == null || !SHARED_STATUSES.contains(letter.getStatus())) {
            throw new IllegalArgumentException("信件不存在或尚不可进行实时回复");
        }
        Long recipientId;
        if (currentUser.userId().equals(letter.getSenderId())) {
            recipientId = letter.getReceiverId();
        } else if (currentUser.userId().equals(letter.getReceiverId())) {
            recipientId = letter.getSenderId();
        } else {
            throw new IllegalArgumentException("无权发送该信件的输入状态");
        }
        UserEntity sender = userMapper.selectById(currentUser.userId());
        String nickname = sender == null || sender.getNickname() == null || sender.getNickname().isBlank()
                ? currentUser.username() : sender.getNickname();
        return new TypingRoute(recipientId, nickname);
    }

    private void stopActiveTyping(WebSocketSession session) {
        ActiveTyping active = activeTyping.remove(session.getId());
        if (active == null) return;
        CurrentUser currentUser = currentUser(session);
        broadcast(active.route().recipientId(), new TypingEvent("typing", currentUser.userId(),
                active.route().senderNickname(), active.letterId(), false));
    }

    private void broadcast(Long userId, Object payload) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null) return;
        sessions.forEach(session -> send(session, payload));
    }

    private void sendError(WebSocketSession session, String message) {
        send(session, Map.of("type", "ERROR", "code", 400, "message", message));
    }

    private void send(WebSocketSession session, Object payload) {
        if (!session.isOpen()) {
            remove(session);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
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

    private record TypingRequest(String type, Long letterId, Boolean typing) { }
    private record TypingEvent(String type, Long senderId, String senderNickname, Long letterId, boolean typing) { }
    private record TypingRoute(Long recipientId, String senderNickname) { }
    private record ActiveTyping(Long letterId, TypingRoute route) { }
}
