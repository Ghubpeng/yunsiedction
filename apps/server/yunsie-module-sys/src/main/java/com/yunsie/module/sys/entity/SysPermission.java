package com.yunsie.module.sys.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 权限表（sys_permission）。权限点编码规范：域:资源:动作（permission-rbac）。
 */
@Getter
@Setter
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    /** 父级ID(0=根) */
    private Long parentId;

    /** 权限点编码: 域:资源:动作, 如 sys:role:create */
    private String permissionCode;

    /** 权限名称 */
    private String permissionName;

    /** 类型: 1-目录 2-菜单 3-按钮/操作 */
    private Integer permType;

    /** 前端路由(菜单用) */
    private String path;

    /** 图标 */
    private String icon;

    /** 排序(越小越靠前) */
    private Integer sort;

    /** 状态: 1-启用 0-禁用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
