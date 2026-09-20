package com.couple.api.websocket;

import com.couple.common.security.CurrentUser;
import com.couple.service.letter.entity.EmotionLetterEntity;
import com.couple.service.letter.mapper.EmotionLetterMapper;
import com.couple.service.user.entity.UserEntity;
import com.couple.service.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypingWebSocketHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void forwardsTypingOnlyToOtherParticipantAndStopsOnDisconnect() throws Exception {
        EmotionLetterEntity letter = letter(11L, 1L, 2L, "READ");
        UserEntity alice = new UserEntity();
        alice.setNickname("爱丽丝");
        EmotionLetterMapper letterMapper = mapper(EmotionLetterMapper.class,
                invocation -> "selectById".equals(invocation.getName()) ? letter : defaultValue(invocation.getReturnType()));
        UserMapper userMapper = mapper(UserMapper.class,
                invocation -> "selectById".equals(invocation.getName()) ? alice : defaultValue(invocation.getReturnType()));
        TypingWebSocketHandler handler = new TypingWebSocketHandler(objectMapper, letterMapper, userMapper);
        List<String> senderMessages = new ArrayList<>();
        List<String> recipientMessages = new ArrayList<>();
        WebSocketSession sender = session("sender", 1L, senderMessages);
        WebSocketSession recipient = session("recipient", 2L, recipientMessages);

        handler.afterConnectionEstablished(sender);
        handler.afterConnectionEstablished(recipient);
        handler.handleTextMessage(sender,
                new org.springframework.web.socket.TextMessage("{\"type\":\"typing\",\"letterId\":11,\"typing\":true}"));

        JsonNode started = objectMapper.readTree(recipientMessages.get(1));
        assertEquals("typing", started.get("type").asText());
        assertEquals(1L, started.get("senderId").asLong());
        assertEquals("爱丽丝", started.get("senderNickname").asText());
        assertEquals(11L, started.get("letterId").asLong());
        assertEquals(true, started.get("typing").asBoolean());
        assertEquals(1, senderMessages.size());

        handler.afterConnectionClosed(sender, CloseStatus.NORMAL);
        JsonNode stopped = objectMapper.readTree(recipientMessages.get(2));
        assertEquals(false, stopped.get("typing").asBoolean());
        assertEquals(0, handler.connectionCount(1L));
    }

    @Test
    void rejectsUnknownOrPrivateLetterWithoutBroadcasting() throws Exception {
        EmotionLetterEntity privateLetter = letter(12L, 7L, 8L, "DRAFT");
        EmotionLetterMapper letterMapper = mapper(EmotionLetterMapper.class,
                invocation -> "selectById".equals(invocation.getName()) ? privateLetter : defaultValue(invocation.getReturnType()));
        UserMapper userMapper = mapper(UserMapper.class, invocation -> defaultValue(invocation.getReturnType()));
        TypingWebSocketHandler handler = new TypingWebSocketHandler(objectMapper, letterMapper, userMapper);
        List<String> senderMessages = new ArrayList<>();
        List<String> recipientMessages = new ArrayList<>();
        WebSocketSession sender = session("sender", 7L, senderMessages);
        WebSocketSession recipient = session("recipient", 8L, recipientMessages);
        handler.afterConnectionEstablished(sender);
        handler.afterConnectionEstablished(recipient);

        handler.handleTextMessage(sender,
                new org.springframework.web.socket.TextMessage("{\"type\":\"typing\",\"letterId\":12,\"typing\":true}"));

        JsonNode error = objectMapper.readTree(senderMessages.get(1));
        assertEquals("ERROR", error.get("type").asText());
        assertEquals(400, error.get("code").asInt());
        assertEquals(1, recipientMessages.size());
    }

    private EmotionLetterEntity letter(Long id, Long senderId, Long receiverId, String status) {
        EmotionLetterEntity letter = new EmotionLetterEntity();
        letter.setId(id);
        letter.setSenderId(senderId);
        letter.setReceiverId(receiverId);
        letter.setStatus(status);
        return letter;
    }

    private WebSocketSession session(String id, Long userId, List<String> messages) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WebSocketAuthHandshakeInterceptor.CURRENT_USER_ATTRIBUTE,
                new CurrentUser(userId, "user-" + userId, "session", Instant.now().plusSeconds(60)));
        return (WebSocketSession) Proxy.newProxyInstance(WebSocketSession.class.getClassLoader(),
                new Class<?>[]{WebSocketSession.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getAttributes" -> attributes;
                    case "isOpen" -> true;
                    case "sendMessage" -> {
                        messages.add(String.valueOf(((WebSocketMessage<?>) args[0]).getPayload()));
                        yield null;
                    }
                    case "getId" -> id;
                    default -> defaultValue(method.getReturnType());
                });
    }

    @SuppressWarnings("unchecked")
    private <T> T mapper(Class<T> type, Function<java.lang.reflect.Method, Object> invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.apply(method));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
