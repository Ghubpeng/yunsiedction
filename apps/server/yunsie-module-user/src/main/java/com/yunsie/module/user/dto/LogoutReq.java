package com.yunsie.module.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登出入参（撤销指定刷新令牌）。
 */
public record LogoutReq(
        @NotBlank(message = "刷新令牌不能为空") String refreshToken) {
}
