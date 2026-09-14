package com.couple.api.websocket;

import com.couple.common.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationWebSocketHandlerTest {
    @Test
    void broadcastsToEveryConnectionAndRemovesClosedSession() throws Exception {
        var handler = new NotificationWebSocketHandler(new ObjectMapper());
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
        var handler = new NotificationWebSocketHandler(new ObjectMapper());
        List<String> messages = new ArrayList<>();
        handler.handleTextMessage(session(4L, messages), new TextMessage("PING"));
        assertEquals(List.of("{\"type\":\"PONG\"}"), messages);
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

    private Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
