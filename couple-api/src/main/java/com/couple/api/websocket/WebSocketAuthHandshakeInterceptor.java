package com.couple.api.websocket;

import com.couple.common.security.CurrentUser;
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

    private final WebSocketTicketService ticketService;

    /** 握手认证只接受短时票据，不再解析 URL 中的 JWT。 */
    public WebSocketAuthHandshakeInterceptor(WebSocketTicketService ticketService) {
        this.ticketService = ticketService;
    }

    /** 消费一次性票据并把认证身份交给后续 WebSocket 处理器。 */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(request.getURI())
                    .build().getQueryParams();
            CurrentUser currentUser = ticketService.consume(query.getFirst("ticket"));
            attributes.put(CURRENT_USER_ATTRIBUTE, currentUser);
            return true;
        } catch (RuntimeException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    /** 握手完成后无需额外清理，票据已在握手前原子消费。 */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需额外清理，连接生命周期由 NotificationWebSocketHandler 管理。
    }
}
