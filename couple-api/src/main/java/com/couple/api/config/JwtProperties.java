package com.couple.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 绑定 {@code jwt.*} 配置项，供认证拦截器读取签名密钥和有效期。 */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** JWT 签名密钥，生产环境应通过安全配置注入。 */
    private String secret;

    /** Token 有效期，单位为秒，默认 24 小时。 */
    private long expire = 86400;

    /** 返回 JWT 签名密钥。 */
    public String getSecret() {
        return secret;
    }

    /** 设置 JWT 签名密钥。 */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /** 返回 Token 有效期，单位为秒。 */
    public long getExpire() {
        return expire;
    }

    /** 设置 Token 有效期，单位为秒。 */
    public void setExpire(long expire) {
        this.expire = expire;
    }
}
