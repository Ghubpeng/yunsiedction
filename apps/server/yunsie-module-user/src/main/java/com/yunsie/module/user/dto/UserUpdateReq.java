package com.yunsie.module.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 更新用户入参（username 创建后不可变）。
 */
public record UserUpdateReq(
        @Size(max = 50, message = "昵称最长50字符") String nickname,
        @Size(max = 255, message = "头像URL最长255字符") String avatar,
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式非法") String mobile,
        @Min(value = 1, message = "用户类型非法") @Max(value = 2, message = "用户类型非法") Integer userType) {
}
