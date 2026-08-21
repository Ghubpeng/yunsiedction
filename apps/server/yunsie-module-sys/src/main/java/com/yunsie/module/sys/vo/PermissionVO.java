package com.yunsie.module.sys.vo;

import java.util.List;

/**
 * 权限 VO（树形）。
 */
public record PermissionVO(
        Long id,
        Long parentId,
        String permissionCode,
        String permissionName,
        Integer permType,
        String path,
        String icon,
        Integer sort,
        Integer status,
        String remark,
        List<PermissionVO> children) {
}
