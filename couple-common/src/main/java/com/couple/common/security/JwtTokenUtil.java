package com.couple.common.security;

import com.couple.common.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** JWT 的唯一封装入口，业务代码不直接操作 JJWT。 */
public final class JwtTokenUtil {

    /** 不允许实例化工具类。 */
    private JwtTokenUtil() {
    }

    /**
     * 创建带有用户身份、签发时间和过期时间的 JWT。
     * @param secret 用于 HMAC 签名的密钥，UTF-8 编码后至少 32 字节
     * @param userId 用户 ID，将作为 JWT subject
     * @param username 用户名，将写入自定义声明
     * @param ttl 从当前时间开始的有效期
     * @return 已签名的紧凑 JWT 字符串
     */
    public static String createToken(String secret, Long userId, String username, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key(secret))
                .compact();
    }

    /**
     * 校验并解析 JWT，提取当前用户身份。
     * @param secret 用于验签的密钥
     * @param token 请求中携带的 JWT 字符串
     * @return 解析得到的当前用户
     * @throws UnauthorizedException Token 格式错误、签名不匹配或已经过期
     */
    public static CurrentUser parse(String secret, String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key(secret))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new CurrentUser(Long.valueOf(claims.getSubject()), claims.get("username", String.class));
        } catch (Exception e) {
            throw new UnauthorizedException("Token 无效或已过期");
        }
    }

    /** 根据密钥创建签名和验签共用的 HMAC 密钥。 */
    private static SecretKey key(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret 至少需要 32 个字节");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
