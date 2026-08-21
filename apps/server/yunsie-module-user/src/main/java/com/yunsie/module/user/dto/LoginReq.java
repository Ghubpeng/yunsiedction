package com.yunsie.module.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录入参（account = 用户名或手机号）。
 */
public record LoginReq(
        @NotBlank(message = "账号不能为空") String account,
        @NotBlank(message = "密码不能为空") String password) {
}
