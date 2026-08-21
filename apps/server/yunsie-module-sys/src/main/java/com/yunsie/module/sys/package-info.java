/**
 * sys 域：系统、RBAC 权限（角色/菜单/按钮权限点/数据权限 DataScope）、字典、审计日志。
 *
 * <p>模块边界规则（project-architecture / 本次骨架约束）：</p>
 * <ol>
 *   <li>本域数据库表（sys_*）只允许本域 Mapper 访问。</li>
 *   <li>其他域只通过本包下 {@code api} 子包暴露的 Application Service 接口交互。</li>
 *   <li>禁止其他域注入本域 Service 实现类或 Mapper。</li>
 * </ol>
 */
package com.yunsie.module.sys;
