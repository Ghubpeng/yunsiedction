package com.yunsie.module.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 用户角色分配入参（经 sys 域契约执行）。
 */
public record UserRolesReq(
        @NotNull(message = "角色ID列表不能为空") List<Long> roleIds) {
}
