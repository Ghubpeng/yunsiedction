package com.yunsie.common.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TokenService 单元测试（access JWT + refresh 不透明串）。
 */
class TokenServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdefghij";

    private TokenService service() {
        return new TokenService(SECRET, 7200, 2592000);
    }

    @Test
    void accessToken_roundTrip() {
        TokenService service = service();
        String token = service.createAccessToken(42L, "alice");
        TokenService.AccessClaims claims = service.parseAccessToken(token);
        assertEquals(42L, claims.userId());
        assertEquals("alice", claims.username());
    }

    @Test
    void accessToken_tampered_throws() {
        TokenService service = service();
        String token = service.createAccessToken(42L, "alice");
        assertThrows(JwtException.class, () -> service.parseAccessToken(token + "x"));
    }

    @Test
    void accessToken_expired_throws() {
        TokenService expired = new TokenService(SECRET, -10, 60);
        String token = expired.createAccessToken(42L, "alice");
        assertThrows(JwtException.class, () -> expired.parseAccessToken(token));
    }

    @Test
    void accessToken_doesNotContainPermissions() {
        // 契约：token 不塞权限列表（仅 userId/username），权限由服务端实时解析
        TokenService service = service();
        String token = service.createAccessToken(42L, "alice");
        TokenService.AccessClaims claims = service.parseAccessToken(token);
        assertEquals(42L, claims.userId());
        assertTrue(!token.contains("sys:user:view"), "token 不得携带权限列表");
    }

    @Test
    void refreshToken_randomAndHashed() {
        TokenService service = service();
        String t1 = service.newRefreshToken();
        String t2 = service.newRefreshToken();
        assertNotEquals(t1, t2);
        String hash = service.hashRefreshToken(t1);
        assertEquals(64, hash.length());
        assertNotEquals(t1, hash);
        assertEquals(hash, service.hashRefreshToken(t1));
    }
}
