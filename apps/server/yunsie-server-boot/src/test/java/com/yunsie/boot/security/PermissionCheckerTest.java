package com.yunsie.boot.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PermissionChecker 单元测试：通配 *:*:* 恒通过（超级管理员）。
 */
class PermissionCheckerTest {

    private final PermissionChecker checker = new PermissionChecker();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void wildcard_passesAnyCode() {
        setAuth(List.of("*:*:*"));
        assertTrue(checker.has("sys:user:view"));
        assertTrue(checker.has("anything:else:do"));
    }

    @Test
    void exactCode_passes() {
        setAuth(List.of("sys:user:view"));
        assertTrue(checker.has("sys:user:view"));
        assertFalse(checker.has("sys:user:delete"));
    }

    @Test
    void missingCode_fails() {
        setAuth(List.of("sys:role:view"));
        assertFalse(checker.has("sys:user:view"));
    }

    @Test
    void unauthenticated_fails() {
        SecurityContextHolder.clearContext();
        assertFalse(checker.has("sys:user:view"));
    }

    @Test
    void blankCode_fails() {
        setAuth(List.of("*:*:*"));
        assertFalse(checker.has(null));
        assertFalse(checker.has(""));
    }

    private void setAuth(List<String> codes) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null,
                        codes.stream().map(SimpleGrantedAuthority::new).toList()));
    }
}
