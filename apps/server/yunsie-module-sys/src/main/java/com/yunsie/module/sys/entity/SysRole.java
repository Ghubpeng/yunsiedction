package com.yunsie.module.sys.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色表（sys_role）。RBAC：用户-角色-权限（permission-rbac）。
 */
@Getter
@Setter
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 角色编码 */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 角色类型: 1-系统内置 2-自定义 */
    private Integer roleType;

    /** 状态: 1-启用 0-禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 排序(越小越靠前) */
    private Integer sort;
}
