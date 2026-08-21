package com.yunsie.module.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 用户状态变更入参。
 */
public record UserStatusReq(
        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态非法") @Max(value = 2, message = "状态非法")
        Integer status) {
}
