package com.yunsie.module.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新令牌入参。
 */
public record RefreshReq(
        @NotBlank(message = "刷新令牌不能为空") String refreshToken) {
}
