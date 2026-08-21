package com.yunsie.module.sys.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色-权限关联表（sys_role_permission）。
 */
@Getter
@Setter
@TableName("sys_role_permission")
public class SysRolePermission extends BaseEntity {

    /** 角色ID */
    private Long roleId;

    /** 权限ID */
    private Long permissionId;
}
