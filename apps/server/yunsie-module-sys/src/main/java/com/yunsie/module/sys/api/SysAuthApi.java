package com.yunsie.module.sys.api;

import java.util.List;

/**
 * 认证/授权契约（sys 域对外 API）：
 * 供 boot 安全过滤器与各业务域使用 —— 用户→角色→权限 与 数据范围统一由 sys 域解析，
 * 禁止其他域直连 sys 表（模块边界铁律）。
 */
public interface SysAuthApi {

    /**
     * 当前用户的权限点编码列表（用户 → 启用角色 → 启用权限，实时解析）。
     * 不含通配判断；*:*:* 由调用方（PermissionChecker）统一处理。
     */
    List<String> listPermissionCodes(Long userId);

    /** 用户角色 ID 列表 */
    List<Long> listRoleIds(Long userId);

    /** 是否拥有"全部数据"范围（任一启用角色配置了 scope_type=1 且 resource_type=*） */
    boolean hasAllData(Long userId);
}
