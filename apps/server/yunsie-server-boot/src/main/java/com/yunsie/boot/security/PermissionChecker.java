package com.yunsie.boot.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 统一权限校验入口（Controller/API 层唯一机制）：
 * {@code @PreAuthorize("@perm.has('sys:user:view')")}。
 * 通配权限 *:*:*（超级管理员）恒通过；禁止业务代码手写角色判断（permission-rbac）。
 */
@Component("perm")
public class PermissionChecker {

    public static final String WILDCARD = "*:*:*";

    public boolean has(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> WILDCARD.equals(a.getAuthority()) || permissionCode.equals(a.getAuthority()));
    }
}
