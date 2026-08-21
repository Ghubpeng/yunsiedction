package com.yunsie.boot.config;

import com.yunsie.common.security.TokenService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 装配：TokenService 为全项目唯一 Token 设施（common 纯逻辑）。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public TokenService tokenService(JwtProperties properties) {
        return new TokenService(properties.getSecret(),
                properties.getAccessTtlSeconds(),
                properties.getRefreshTtlSeconds());
    }
}
