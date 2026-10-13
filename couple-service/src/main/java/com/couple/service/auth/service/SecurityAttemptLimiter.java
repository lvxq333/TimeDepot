package com.couple.service.auth.service;

import com.couple.common.exception.BusinessException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/** 复用现有 Redis，为敏感账户操作提供跨实例共享的固定窗口限流。 */
@Service
public class SecurityAttemptLimiter {
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]); "
                    + "if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end; return n",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    /** 使用项目已有的 Redis 连接，不引入新的存储服务。 */
    public SecurityAttemptLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 原子地计入本次尝试；Redis 不可用时拒绝敏感操作，避免绕过次数限制。
     */
    public void check(String action, String subject, int maximum, Duration window) {
        String key = "couple:security:" + action + ":" + sha256(subject);
        try {
            // INCR 与首次设置过期时间在同一个脚本内完成，防止并发时产生永不过期的计数键。
            Long count = redisTemplate.execute(INCREMENT_SCRIPT, List.of(key),
                    String.valueOf(window.toSeconds()));
            if (count == null) {
                throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "安全校验暂不可用，请稍后重试");
            }
            if (count > maximum) {
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁，请稍后重试");
            }
        } catch (DataAccessException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "安全校验暂不可用，请稍后重试");
        }
    }

    /** 对用户名和来源地址做摘要，避免将原值直接写入 Redis 键名。 */
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }
}
