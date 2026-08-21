package com.yunsie.module.sys.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 角色分配权限入参。
 */
public record RolePermissionsReq(
        @NotNull(message = "权限ID列表不能为空") List<Long> permissionIds) {
}
