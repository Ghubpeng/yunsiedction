package com.yunsie.module.sys.service;

import com.yunsie.module.sys.dto.PermissionCreateReq;
import com.yunsie.module.sys.dto.PermissionUpdateReq;
import com.yunsie.module.sys.vo.PermissionVO;

import java.util.List;

/**
 * 权限管理（sys 域内部服务，不对外跨域暴露）。
 */
public interface SysPermissionService {

    Long create(PermissionCreateReq req);

    void update(Long id, PermissionUpdateReq req);

    /** 删除前校验：有子权限禁止删除；同时清理角色关联 */
    void delete(Long id);

    PermissionVO detail(Long id);

    /** 权限树（目录/菜单/按钮按 parent_id 组装） */
    List<PermissionVO> tree();
}
