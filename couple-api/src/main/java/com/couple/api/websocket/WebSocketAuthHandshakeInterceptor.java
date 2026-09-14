package com.couple.api.websocket;

import com.couple.api.config.JwtProperties;
import com.couple.common.security.CurrentUser;
import com.couple.common.security.JwtTokenUtil;
import com.couple.service.auth.service.AuthSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/** 在 WebSocket 升级前复用 HTTP 登录态校验，并把当前用户写入连接属性。 */
@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String CURRENT_USER_ATTRIBUTE = "currentUser";

    private final JwtProperties jwtProperties;
    private final AuthSessionService authSessionService;

    public WebSocketAuthHandshakeInterceptor(JwtProperties jwtProperties,
                                             AuthSessionService authSessionService) {
        this.jwtProperties = jwtProperties;
        this.authSessionService = authSessionService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(request.getURI())
                    .build().getQueryParams();
            String token = query.getFirst("token");
            if (token == null || token.isBlank()) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            CurrentUser currentUser = JwtTokenUtil.parse(jwtProperties.getSecret(), token);
            authSessionService.requireActiveSession(currentUser);
            attributes.put(CURRENT_USER_ATTRIBUTE, currentUser);
            return true;
        } catch (RuntimeException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需额外清理，连接生命周期由 NotificationWebSocketHandler 管理。
    }
}
