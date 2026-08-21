package com.yunsie.module.sys.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新角色入参（role_code 创建后不可变）。
 */
public record RoleUpdateReq(
        @NotBlank(message = "角色名称不能为空") @Size(max = 50, message = "角色名称最长50字符") String roleName,
        @Min(value = 0, message = "状态非法") @Max(value = 1, message = "状态非法") Integer status,
        @Size(max = 255, message = "备注最长255字符") String remark,
        Integer sort) {
}
