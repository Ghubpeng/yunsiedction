package com.yunsie.module.sys.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户-角色关联表（sys_user_role）。
 * 表归 sys 域所有；user 等业务域只能通过 api 包契约 {@code SysUserRoleApi} 操作，
 * 禁止直连本表（模块边界铁律）。
 */
@Getter
@Setter
@TableName("sys_user_role")
public class SysUserRole extends BaseEntity {

    /** 用户ID(user域, 无物理外键) */
    private Long userId;

    /** 角色ID */
    private Long roleId;
}
