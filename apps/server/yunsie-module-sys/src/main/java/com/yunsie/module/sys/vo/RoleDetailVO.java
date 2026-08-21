package com.yunsie.module.sys.vo;

import java.util.List;

/**
 * 角色详情 VO（角色 + 权限ID + 数据范围）。
 */
public record RoleDetailVO(
        RoleVO role,
        List<Long> permissionIds,
        List<DataScopeVO> dataScopes) {
}
