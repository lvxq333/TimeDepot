package com.couple.service.auth.service.impl;

import com.couple.common.exception.BusinessException;
import com.couple.common.exception.UnauthorizedException;
import com.couple.common.security.CurrentUser;
import com.couple.service.auth.entity.AuthSessionEntity;
import com.couple.service.auth.mapper.AuthSessionMapper;
import com.couple.service.auth.service.AuthSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** 使用 MySQL 持久化会话，并以 Redis 缓存降低认证查询开销。 */
@Service
public class AuthSessionServiceImpl implements AuthSessionService {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionServiceImpl.class);
    private static final String ACTIVE = "ACTIVE";
    private static final String REVOKED = "REVOKED";
    private static final String PREFIX = "couple:auth:";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final AuthSessionMapper sessionMapper;
    private final StringRedisTemplate redisTemplate;
    private final long cacheTtlSeconds;
    private final long missingTtlSeconds;
    private final long ttlJitterSeconds;
    private final long lockTtlSeconds;

    public AuthSessionServiceImpl(
            AuthSessionMapper sessionMapper,
            StringRedisTemplate redisTemplate,
            @Value("${auth.session.cache-ttl-seconds:1800}") long cacheTtlSeconds,
            @Value("${auth.session.missing-ttl-seconds:45}") long missingTtlSeconds,
            @Value("${auth.session.ttl-jitter-seconds:120}") long ttlJitterSeconds,
            @Value("${auth.session.lock-ttl-seconds:3}") long lockTtlSeconds) {
        this.sessionMapper = sessionMapper;
        this.redisTemplate = redisTemplate;
        this.cacheTtlSeconds = Math.max(60, cacheTtlSeconds);
        this.missingTtlSeconds = Math.max(10, missingTtlSeconds);
        this.ttlJitterSeconds = Math.max(0, ttlJitterSeconds);
        this.lockTtlSeconds = Math.max(1, lockTtlSeconds);
    }

    /** 先持久化会话；缓存采用首次认证时懒加载，避免事务提交前产生脏缓存。 */
    @Override
    @Transactional
    public String createSession(Long userId, String username, Duration ttl) {
        LocalDateTime now = LocalDateTime.now();
        AuthSessionEntity session = new AuthSessionEntity();
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setUsername(username);
        session.setStatus(ACTIVE);
        session.setIssuedAt(now);
        session.setExpiresAt(now.plus(ttl));
        session.setLastActiveAt(now);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        if (sessionMapper.insert(session) != 1) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "登录会话创建失败");
        }
        return session.getSessionId();
    }

    @Override
    public void requireActiveSession(CurrentUser currentUser) {
        CacheResult cached = readCacheSafely(currentUser.sessionId());
        if (cached.state() == CacheState.INVALID) {
            throw new UnauthorizedException("登录会话已失效");
        }
        if (cached.state() == CacheState.ACTIVE) {
            validateIdentity(currentUser, cached.session());
            return;
        }
        if (cached.state() == CacheState.UNAVAILABLE) {
            loadAndValidateFromDatabase(currentUser);
            return;
        }
        loadFromDatabaseWithMutex(currentUser);
    }

    /** 撤销标记必须先写入 Redis，防止删除正缓存失败时旧会话继续通过认证。 */
    @Override
    @Transactional
    public void revokeCurrentSession(CurrentUser currentUser) {
        AuthSessionEntity session = sessionMapper.selectBySessionId(currentUser.sessionId());
        if (session != null && REVOKED.equals(session.getStatus())
                && currentUser.userId().equals(session.getUserId())) {
            return;
        }
        validateDatabaseSession(currentUser, session);

        long remaining = Math.max(60, Duration.between(
                LocalDateTime.now(), session.getExpiresAt()).toSeconds());
        try {
            redisTemplate.opsForValue().set(revokedKey(currentUser.sessionId()), "1",
                    Duration.ofSeconds(remaining));
        } catch (DataAccessException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "会话服务暂不可用，请稍后重试");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = sessionMapper.revoke(currentUser.sessionId(), currentUser.userId(),
                "USER_LOGOUT", now);
        if (updated == 0) {
            AuthSessionEntity latest = sessionMapper.selectBySessionId(currentUser.sessionId());
            if (latest == null || !REVOKED.equals(latest.getStatus())) {
                throw new UnauthorizedException("登录会话已失效");
            }
        }
        try {
            redisTemplate.delete(List.of(sessionKey(currentUser.sessionId()),
                    missingKey(currentUser.sessionId())));
        } catch (DataAccessException exception) {
            log.warn("撤销标记已生效，但清理会话缓存失败: sessionId={}", currentUser.sessionId());
        }
    }

    @Override
    @Transactional
    public void revokeAllSessions(Long userId) {
        for (AuthSessionEntity session : sessionMapper.selectActiveByUserId(userId)) {
            revokeCurrentSession(new CurrentUser(userId, session.getUsername(),
                    session.getSessionId(), Instant.now().plusSeconds(3600)));
        }
    }

    private void loadFromDatabaseWithMutex(CurrentUser currentUser) {
        String lockValue = UUID.randomUUID().toString();
        boolean locked = tryLock(currentUser.sessionId(), lockValue);
        try {
            if (locked) {
                CacheResult secondRead = readCacheSafely(currentUser.sessionId());
                if (secondRead.state() == CacheState.INVALID) {
                    throw new UnauthorizedException("登录会话已失效");
                }
                if (secondRead.state() == CacheState.ACTIVE) {
                    validateIdentity(currentUser, secondRead.session());
                    return;
                }
            } else {
                waitForConcurrentLoad();
                CacheResult concurrentResult = readCacheSafely(currentUser.sessionId());
                if (concurrentResult.state() == CacheState.INVALID) {
                    throw new UnauthorizedException("登录会话已失效");
                }
                if (concurrentResult.state() == CacheState.ACTIVE) {
                    validateIdentity(currentUser, concurrentResult.session());
                    return;
                }
            }
            loadAndValidateFromDatabase(currentUser);
        } finally {
            if (locked) {
                unlock(currentUser.sessionId(), lockValue);
            }
        }
    }

    private void loadAndValidateFromDatabase(CurrentUser currentUser) {
        AuthSessionEntity session = sessionMapper.selectBySessionId(currentUser.sessionId());
        try {
            validateDatabaseSession(currentUser, session);
        } catch (UnauthorizedException exception) {
            cacheInvalidSession(currentUser.sessionId(), session);
            throw exception;
        }
        cacheActiveSession(session);
    }

    private void validateDatabaseSession(CurrentUser currentUser, AuthSessionEntity session) {
        if (session == null || !ACTIVE.equals(session.getStatus())
                || session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new UnauthorizedException("登录会话已失效");
        }
        validateIdentity(currentUser, snapshot(session));
    }

    private void validateIdentity(CurrentUser currentUser, SessionSnapshot session) {
        if (!currentUser.userId().equals(session.userId())
                || !currentUser.username().equals(session.username())
                || !session.expiresAt().isAfter(LocalDateTime.now())) {
            throw new UnauthorizedException("登录会话与 Token 不匹配");
        }
    }

    private CacheResult readCacheSafely(String sessionId) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(revokedKey(sessionId)))) {
                return CacheResult.invalid();
            }
            String cachedValue = redisTemplate.opsForValue().get(sessionKey(sessionId));
            if (cachedValue != null) {
                String[] fields = cachedValue.split("\\t", -1);
                if (fields.length != 3) {
                    throw new IllegalArgumentException("会话缓存格式错误");
                }
                return CacheResult.active(new SessionSnapshot(
                        Long.valueOf(fields[0]), fields[1],
                        LocalDateTime.ofEpochSecond(Long.parseLong(fields[2]), 0, ZoneOffset.UTC)));
            }
            if (Boolean.TRUE.equals(redisTemplate.hasKey(missingKey(sessionId)))) {
                return CacheResult.invalid();
            }
            return CacheResult.miss();
        } catch (RuntimeException exception) {
            log.warn("读取 Redis 会话缓存失败，回源 MySQL: sessionId={}", sessionId);
            return CacheResult.unavailable();
        }
    }

    private void cacheActiveSession(AuthSessionEntity session) {
        long remaining = Duration.between(LocalDateTime.now(), session.getExpiresAt()).toSeconds();
        if (remaining <= 0) return;
        long ttl = Math.min(remaining, cacheTtlSeconds + randomJitter(ttlJitterSeconds));
        try {
            String cachedValue = session.getUserId() + "\t" + session.getUsername() + "\t"
                    + session.getExpiresAt().toEpochSecond(ZoneOffset.UTC);
            redisTemplate.opsForValue().set(sessionKey(session.getSessionId()), cachedValue,
                    Duration.ofSeconds(Math.max(1, ttl)));
        } catch (RuntimeException exception) {
            log.warn("写入 Redis 会话缓存失败: sessionId={}", session.getSessionId());
        }
    }

    private void cacheInvalidSession(String sessionId, AuthSessionEntity session) {
        try {
            if (session != null && REVOKED.equals(session.getStatus()) && session.getExpiresAt() != null) {
                long remaining = Math.max(60,
                        Duration.between(LocalDateTime.now(), session.getExpiresAt()).toSeconds());
                redisTemplate.opsForValue().set(revokedKey(sessionId), "1", Duration.ofSeconds(remaining));
            } else {
                long ttl = missingTtlSeconds + randomJitter(Math.min(15, ttlJitterSeconds));
                redisTemplate.opsForValue().set(missingKey(sessionId), "1", Duration.ofSeconds(ttl));
            }
        } catch (RuntimeException exception) {
            log.warn("写入 Redis 无效会话缓存失败: sessionId={}", sessionId);
        }
    }

    private boolean tryLock(String sessionId, String lockValue) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                    lockKey(sessionId), lockValue, Duration.ofSeconds(lockTtlSeconds)));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void unlock(String sessionId, String lockValue) {
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey(sessionId)), lockValue);
        } catch (RuntimeException exception) {
            log.warn("释放会话回源锁失败: sessionId={}", sessionId);
        }
    }

    private void waitForConcurrentLoad() {
        try {
            Thread.sleep(40);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private long randomJitter(long upperBound) {
        return upperBound == 0 ? 0 : ThreadLocalRandom.current().nextLong(upperBound + 1);
    }

    private SessionSnapshot snapshot(AuthSessionEntity session) {
        return new SessionSnapshot(session.getUserId(), session.getUsername(), session.getExpiresAt());
    }

    private String sessionKey(String sessionId) { return PREFIX + "session:" + sessionId; }
    private String revokedKey(String sessionId) { return PREFIX + "revoked:" + sessionId; }
    private String missingKey(String sessionId) { return PREFIX + "missing:" + sessionId; }
    private String lockKey(String sessionId) { return PREFIX + "lock:" + sessionId; }

    private enum CacheState { ACTIVE, INVALID, MISS, UNAVAILABLE }

    private record SessionSnapshot(Long userId, String username, LocalDateTime expiresAt) {}

    private record CacheResult(CacheState state, SessionSnapshot session) {
        private static CacheResult active(SessionSnapshot session) {
            return new CacheResult(CacheState.ACTIVE, session);
        }
        private static CacheResult invalid() { return new CacheResult(CacheState.INVALID, null); }
        private static CacheResult miss() { return new CacheResult(CacheState.MISS, null); }
        private static CacheResult unavailable() {
            return new CacheResult(CacheState.UNAVAILABLE, null);
        }
    }
}
