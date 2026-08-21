package com.yunsie.module.sys.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.sys.api.SysAuthApi;
import com.yunsie.module.sys.entity.SysDataScope;
import com.yunsie.module.sys.entity.SysPermission;
import com.yunsie.module.sys.entity.SysRole;
import com.yunsie.module.sys.entity.SysRolePermission;
import com.yunsie.module.sys.entity.SysUserRole;
import com.yunsie.module.sys.mapper.SysDataScopeMapper;
import com.yunsie.module.sys.mapper.SysPermissionMapper;
import com.yunsie.module.sys.mapper.SysRoleMapper;
import com.yunsie.module.sys.mapper.SysRolePermissionMapper;
import com.yunsie.module.sys.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证/授权契约实现（permission-rbac）：
 * 权限与数据范围始终从数据库实时解析（token 不携带权限列表）；
 * 只统计启用角色（status=1）与启用权限（status=1）。
 */
@Service
@RequiredArgsConstructor
public class SysAuthServiceImpl implements SysAuthApi {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysDataScopeMapper dataScopeMapper;

    @Override
    public List<String> listPermissionCodes(Long userId) {
        List<Long> enabledRoleIds = enabledRoleIds(userId);
        if (enabledRoleIds.isEmpty()) {
            return List.of();
        }
        List<Long> permissionIds = rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<SysRolePermission>().in(SysRolePermission::getRoleId, enabledRoleIds))
                .stream().map(SysRolePermission::getPermissionId).distinct().toList();
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectList(
                        new LambdaQueryWrapper<SysPermission>()
                                .in(SysPermission::getId, permissionIds)
                                .eq(SysPermission::getStatus, 1))
                .stream().map(SysPermission::getPermissionCode).distinct().toList();
    }

    @Override
    public List<Long> listRoleIds(Long userId) {
        return userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
    }

    @Override
    public boolean hasAllData(Long userId) {
        List<Long> enabledRoleIds = enabledRoleIds(userId);
        if (enabledRoleIds.isEmpty()) {
            return false;
        }
        Long count = dataScopeMapper.selectCount(
                new LambdaQueryWrapper<SysDataScope>()
                        .in(SysDataScope::getRoleId, enabledRoleIds)
                        .eq(SysDataScope::getScopeType, 1)
                        .eq(SysDataScope::getResourceType, "*"));
        return count != null && count > 0;
    }

    private List<Long> enabledRoleIds(Long userId) {
        List<Long> roleIds = listRoleIds(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectList(
                        new LambdaQueryWrapper<SysRole>()
                                .in(SysRole::getId, roleIds)
                                .eq(SysRole::getStatus, 1))
                .stream().map(SysRole::getId).toList();
    }
}
