package com.couple.api.websocket;

import com.couple.common.security.CurrentUser;
import com.couple.common.exception.UnauthorizedException;
import com.couple.service.auth.service.AuthSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationWebSocketHandlerTest {
    @Test
    void broadcastsToEveryConnectionAndRemovesClosedSession() throws Exception {
        var handler = new NotificationWebSocketHandler(new ObjectMapper(), sessions());
        List<String> firstMessages = new ArrayList<>();
        WebSocketSession first = session(3L, firstMessages);
        WebSocketSession second = session(3L, new ArrayList<>());
        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);
        handler.sendToUser(3L, Map.of("type", "LETTER_SENT", "letterId", 19L));
        assertEquals(2, handler.connectionCount(3L));
        assertEquals("LETTER_SENT", new ObjectMapper().readTree(firstMessages.get(1)).get("type").asText());
        assertEquals(19L, new ObjectMapper().readTree(firstMessages.get(1)).get("letterId").asLong());
        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        assertEquals(1, handler.connectionCount(3L));
    }

    @Test
    void respondsToHeartbeat() throws Exception {
        var handler = new NotificationWebSocketHandler(new ObjectMapper(), sessions());
        List<String> messages = new ArrayList<>();
        handler.handleTextMessage(session(4L, messages), new TextMessage("PING"));
        assertEquals(List.of("{\"type\":\"PONG\"}"), messages);
    }

    @Test
    void revokedSessionStopsReceivingNotifications() throws Exception {
        AuthSessionService revoked = new AuthSessionService() {
            public String createSession(Long userId, String username, Duration ttl) { return null; }
            public void requireActiveSession(CurrentUser user) {
                throw new UnauthorizedException("登录会话已失效");
            }
            public void revokeCurrentSession(CurrentUser user) { }
            public void revokeAllSessions(Long userId) { }
        };
        var handler = new NotificationWebSocketHandler(new ObjectMapper(), revoked);
        List<String> messages = new ArrayList<>();
        WebSocketSession connection = session(4L, messages);
        handler.afterConnectionEstablished(connection);
        handler.sendToUser(4L, Map.of("type", "PRIVATE_EVENT"));
        assertEquals(1, messages.size());
        assertEquals(0, handler.connectionCount(4L));
    }

    private WebSocketSession session(Long userId, List<String> messages) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WebSocketAuthHandshakeInterceptor.CURRENT_USER_ATTRIBUTE,
                new CurrentUser(userId, "user", "session", Instant.now().plusSeconds(60)));
        return (WebSocketSession) Proxy.newProxyInstance(WebSocketSession.class.getClassLoader(),
                new Class<?>[]{WebSocketSession.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getAttributes" -> attributes;
                    case "isOpen" -> true;
                    case "sendMessage" -> {
                        messages.add(String.valueOf(((WebSocketMessage<?>) args[0]).getPayload()));
                        yield null;
                    }
                    case "getId" -> "test-" + userId;
                    default -> defaultValue(method.getReturnType());
                });
    }

    /** 普通广播测试使用始终有效的登录会话。 */
    private AuthSessionService sessions() {
        return new AuthSessionService() {
            public String createSession(Long userId, String username, Duration ttl) { return null; }
            public void requireActiveSession(CurrentUser user) { }
            public void revokeCurrentSession(CurrentUser user) { }
            public void revokeAllSessions(Long userId) { }
        };
    }

    private Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
