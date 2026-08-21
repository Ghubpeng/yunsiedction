package com.yunsie.module.sys.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 角色数据权限范围入参（DataScope）。
 */
public record RoleDataScopeReq(
        @NotNull(message = "范围类型不能为空")
        @Min(value = 1, message = "范围类型非法") @Max(value = 2, message = "范围类型非法")
        Integer scopeType,
        List<@Valid ScopeItem> scopes) {

    public record ScopeItem(
            @NotBlank(message = "资源类型不能为空") @Size(max = 50, message = "资源类型最长50字符") String resourceType,
            Long resourceId) {
    }
}
