package com.yunsie.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员重置用户密码入参。
 */
public record UserPasswordResetReq(
        @NotBlank(message = "新密码不能为空") @Size(min = 8, max = 64, message = "密码长度8-64")
        String newPassword) {
}
