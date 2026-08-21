package com.yunsie.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

/**
 * Token 基础设施（common，纯逻辑无 Spring 依赖）。
 *
 * <p>方案（api-design：access 短效 + refresh 长效轮换）：</p>
 * <ul>
 *   <li>access token：JWT（HMAC-SHA256），仅携带 userId/username ——
 *       <b>不塞权限列表</b>，权限由服务端按用户实时解析（permission-rbac）。</li>
 *   <li>refresh token：不透明随机串；服务端只存 SHA-256 哈希（不存明文 token），
 *       支持轮换与撤销，存储见 user 域 user_refresh_token 表 ——
 *       <b>不使用 Redis 会话/黑名单</b>（原因：单实例起步，DB 哈希即可满足撤销需求，
 *       避免为认证引入额外基础设施；access 短 TTL 天然收敛泄露风险）。</li>
 * </ul>
 */
public class TokenService {

    /** access token 载荷 */
    public record AccessClaims(Long userId, String username) {
    }

    private final SecretKey key;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(String secret, long accessTtlSeconds, long refreshTtlSeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("jwt secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    /** 创建 access token（JWT） */
    public String createAccessToken(Long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("yunsie")
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key)
                .compact();
    }

    /** 解析并校验 access token；无效/篡改/过期抛 {@link JwtException} */
    public AccessClaims parseAccessToken(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return new AccessClaims(Long.valueOf(claims.getSubject()),
                claims.get("username", String.class));
    }

    /** 生成 refresh token（不透明随机串，Base64URL，96 字符长度） */
    public String newRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** refresh token 的 SHA-256 哈希（64 位 hex），服务端仅存哈希 */
    public String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public long accessTtlSeconds() {
        return accessTtlSeconds;
    }

    public long refreshTtlSeconds() {
        return refreshTtlSeconds;
    }
}
