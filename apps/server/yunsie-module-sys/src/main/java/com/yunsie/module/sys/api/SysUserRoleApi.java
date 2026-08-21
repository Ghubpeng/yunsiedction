package com.yunsie.module.sys.api;

import java.util.List;

/**
 * 用户-角色关联契约（sys 域对外 API，模块边界铁律的落地）。
 * sys_user_role 表归 sys 域所有；user 等业务域只经本接口操作，禁止直连 sys 表。
 */
public interface SysUserRoleApi {

    /** 覆盖式分配角色（校验角色存在；空列表=清空） */
    void assignRoles(Long userId, List<Long> roleIds);

    /** 查询用户角色ID列表 */
    List<Long> listRoleIds(Long userId);

    /** 删除用户的全部角色关联 */
    void removeByUser(Long userId);
}
