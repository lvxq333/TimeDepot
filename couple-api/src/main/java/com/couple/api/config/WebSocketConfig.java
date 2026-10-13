package com.couple.api.config;

import com.couple.api.websocket.NotificationWebSocketHandler;
import com.couple.api.websocket.TypingWebSocketHandler;
import com.couple.api.websocket.WebSocketAuthHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** 注册实时通知端点；生产环境的来源限制可通过网关继续收紧。 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationHandler;
    private final TypingWebSocketHandler typingHandler;
    private final WebSocketAuthHandshakeInterceptor authInterceptor;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(NotificationWebSocketHandler notificationHandler,
                           TypingWebSocketHandler typingHandler,
                           WebSocketAuthHandshakeInterceptor authInterceptor,
                           @Value("${app.websocket.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
                           String allowedOriginPatterns) {
        this.notificationHandler = notificationHandler;
        this.typingHandler = typingHandler;
        this.authInterceptor = authInterceptor;
        this.allowedOriginPatterns = allowedOriginPatterns.split(",");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationHandler, "/ws/notification")
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns(allowedOriginPatterns);
        registry.addHandler(typingHandler, "/ws/typing")
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }
}
