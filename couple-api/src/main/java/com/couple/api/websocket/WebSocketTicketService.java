package com.couple.api.websocket;

import com.couple.common.exception.BusinessException;
import com.couple.common.exception.UnauthorizedException;
import com.couple.common.security.CurrentUser;
import com.couple.service.auth.service.AuthSessionService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/** 为 WebSocket 握手签发短时一次性票据，避免把长期 JWT 放进 URL。 */
@Service
public class WebSocketTicketService {
    private static final Duration TICKET_TTL = Duration.ofSeconds(30);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); "
                    + "if value then redis.call('DEL', KEYS[1]) end; return value",
            String.class);

    private final StringRedisTemplate redisTemplate;
    private final AuthSessionService authSessionService;

    /** 注入现有 Redis 和登录会话服务。 */
    public WebSocketTicketService(StringRedisTemplate redisTemplate,
                                  AuthSessionService authSessionService) {
        this.redisTemplate = redisTemplate;
        this.authSessionService = authSessionService;
    }

    /** 在当前登录会话仍有效时签发 30 秒票据；每条连接各自申请一张。 */
    public String issue(CurrentUser user) {
        authSessionService.requireActiveSession(user);
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String value = user.userId() + "\t" + user.username() + "\t"
                + user.sessionId() + "\t" + user.expiresAt().toEpochMilli();
        try {
            redisTemplate.opsForValue().set(key(ticket), value, TICKET_TTL);
        } catch (DataAccessException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "实时连接暂不可用");
        }
        return ticket;
    }

    /** 原子消费票据，并再次核对服务端会话，拒绝重放和退出后的连接。 */
    public CurrentUser consume(String ticket) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}")) {
            throw new UnauthorizedException("连接票据无效");
        }
        String value;
        try {
            // GET 与 DEL 必须原子执行，两个同时到达的握手只能有一个成功。
            value = redisTemplate.execute(CONSUME_SCRIPT, List.of(key(ticket)));
        } catch (DataAccessException exception) {
            throw new UnauthorizedException("连接票据暂不可用");
        }
        if (value == null) throw new UnauthorizedException("连接票据无效或已使用");
        try {
            String[] fields = value.split("\t", -1);
            if (fields.length != 4) throw new IllegalArgumentException("票据格式错误");
            CurrentUser user = new CurrentUser(Long.valueOf(fields[0]), fields[1], fields[2],
                    Instant.ofEpochMilli(Long.parseLong(fields[3])));
            authSessionService.requireActiveSession(user);
            return user;
        } catch (RuntimeException exception) {
            throw new UnauthorizedException("连接票据无效");
        }
    }

    /** 将随机票据映射为有 TTL 的 Redis 键。 */
    private String key(String ticket) {
        return "couple:ws-ticket:" + ticket;
    }
}
