package com.yunsie.module.sys.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新权限入参（permission_code / perm_type / parent_id 创建后不可变）。
 */
public record PermissionUpdateReq(
        @NotBlank(message = "权限名称不能为空") @Size(max = 100, message = "权限名称最长100字符") String permissionName,
        @Size(max = 200, message = "路由最长200字符") String path,
        @Size(max = 100, message = "图标最长100字符") String icon,
        Integer sort,
        @Min(value = 0, message = "状态非法") @Max(value = 1, message = "状态非法") Integer status,
        @Size(max = 255, message = "备注最长255字符") String remark) {
}
