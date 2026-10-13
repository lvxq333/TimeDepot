package com.couple.api.websocket;

import com.couple.common.exception.UnauthorizedException;
import com.couple.common.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketAuthHandshakeInterceptorTest {
    private static final String TICKET = "A".repeat(43);

    @Test
    void acceptsActiveSessionAndExposesCurrentUser() {
        CurrentUser expected = new CurrentUser(7L, "alice", "session-1", Instant.now().plusSeconds(60));
        var interceptor = new WebSocketAuthHandshakeInterceptor(new WebSocketTicketService(null, null) {
            @Override public CurrentUser consume(String ticket) {
                assertEquals(TICKET, ticket);
                return expected;
            }
        });
        Map<String, Object> attributes = new HashMap<>();
        assertTrue(interceptor.beforeHandshake(request("ws://localhost/ws/notification?ticket=" + TICKET),
                response(new AtomicReference<>()), handler(), attributes));
        CurrentUser user = (CurrentUser) attributes.get(WebSocketAuthHandshakeInterceptor.CURRENT_USER_ATTRIBUTE);
        assertEquals(7L, user.userId());
        assertEquals("session-1", user.sessionId());
    }

    @Test
    void rejectsMissingOrRevokedSession() {
        AtomicReference<HttpStatusCode> missing = new AtomicReference<>();
        AtomicReference<HttpStatusCode> revoked = new AtomicReference<>();
        var interceptor = new WebSocketAuthHandshakeInterceptor(new WebSocketTicketService(null, null) {
            @Override public CurrentUser consume(String ticket) {
                throw new UnauthorizedException(ticket == null ? "missing" : "revoked");
            }
        });
        assertFalse(interceptor.beforeHandshake(request("ws://localhost/ws/notification"), response(missing),
                handler(), new HashMap<>()));
        assertFalse(interceptor.beforeHandshake(request("ws://localhost/ws/notification?ticket=" + TICKET),
                response(revoked), handler(), new HashMap<>()));
        assertEquals(401, missing.get().value());
        assertEquals(401, revoked.get().value());
    }

    private ServerHttpRequest request(String uri) {
        return proxy(ServerHttpRequest.class, (method, args) ->
                "getURI".equals(method.getName()) ? URI.create(uri) : defaultValue(method.getReturnType()));
    }

    private ServerHttpResponse response(AtomicReference<HttpStatusCode> status) {
        return proxy(ServerHttpResponse.class, (method, args) -> {
            if ("setStatusCode".equals(method.getName())) status.set((HttpStatusCode) args[0]);
            return defaultValue(method.getReturnType());
        });
    }

    private WebSocketHandler handler() {
        return proxy(WebSocketHandler.class, (method, args) -> defaultValue(method.getReturnType()));
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.invoke(method, args));
    }

    private Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }

    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }
}
