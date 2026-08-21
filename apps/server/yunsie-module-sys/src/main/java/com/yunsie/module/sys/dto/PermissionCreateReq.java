package com.yunsie.module.sys.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建权限入参。
 */
public record PermissionCreateReq(
        Long parentId,
        @NotBlank(message = "权限编码不能为空") @Size(max = 100, message = "权限编码最长100字符") String permissionCode,
        @NotBlank(message = "权限名称不能为空") @Size(max = 100, message = "权限名称最长100字符") String permissionName,
        @NotNull(message = "权限类型不能为空")
        @Min(value = 1, message = "权限类型非法") @Max(value = 3, message = "权限类型非法")
        Integer permType,
        @Size(max = 200, message = "路由最长200字符") String path,
        @Size(max = 100, message = "图标最长100字符") String icon,
        Integer sort,
        @Min(value = 0, message = "状态非法") @Max(value = 1, message = "状态非法") Integer status,
        @Size(max = 255, message = "备注最长255字符") String remark) {
}
