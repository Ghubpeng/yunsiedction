package com.yunsie.module.sys.service;

import com.yunsie.common.api.PageResult;
import com.yunsie.module.sys.dto.RoleCreateReq;
import com.yunsie.module.sys.dto.RoleDataScopeReq;
import com.yunsie.module.sys.dto.RoleUpdateReq;
import com.yunsie.module.sys.vo.RoleDetailVO;
import com.yunsie.module.sys.vo.RoleVO;

import java.util.List;

/**
 * 角色管理（sys 域内部服务，不对外跨域暴露）。
 */
public interface SysRoleService {

    Long create(RoleCreateReq req);

    void update(Long id, RoleUpdateReq req);

    void delete(Long id);

    PageResult<RoleVO> page(int pageNum, int pageSize, String keyword);

    RoleDetailVO detail(Long id);

    /** 覆盖式分配权限（事务内先清后建，避免脏关联） */
    void assignPermissions(Long roleId, List<Long> permissionIds);

    /** 覆盖式配置数据权限范围 */
    void assignDataScope(Long roleId, RoleDataScopeReq req);
}
