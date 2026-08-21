package com.yunsie.boot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置（yunsie.jwt.*）。
 * 生产环境必须经环境变量 YUNSIE_JWT_SECRET 注入，禁止使用默认开发密钥。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "yunsie.jwt")
public class JwtProperties {

    /** HMAC 密钥（≥32 字节） */
    private String secret;

    /** access token 生命周期（秒），默认 2 小时 */
    private long accessTtlSeconds = 7200;

    /** refresh token 生命周期（秒），默认 30 天 */
    private long refreshTtlSeconds = 2592000;
}
