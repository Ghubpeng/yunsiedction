package com.yunsie.module.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建用户入参（管理员创建账号；roleIds 经 sys 域契约 SysUserRoleApi 分配）。
 */
public record UserCreateReq(
        @NotBlank(message = "账号不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_]{4,50}$", message = "账号仅限字母数字下划线，长度4-50")
        String username,
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式非法")
        String mobile,
        @Size(max = 50, message = "昵称最长50字符") String nickname,
        @NotBlank(message = "密码不能为空") @Size(min = 8, max = 64, message = "密码长度8-64")
        String password,
        @Min(value = 1, message = "用户类型非法") @Max(value = 3, message = "用户类型非法") Integer userType,
        List<Long> roleIds) {
}
