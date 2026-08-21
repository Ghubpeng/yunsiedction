package com.yunsie.module.sys.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.sys.api.SysUserRoleApi;
import com.yunsie.module.sys.entity.SysRole;
import com.yunsie.module.sys.entity.SysUserRole;
import com.yunsie.module.sys.error.SysErrorCode;
import com.yunsie.module.sys.mapper.SysRoleMapper;
import com.yunsie.module.sys.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户-角色关联契约实现（sys 域内部；对外只暴露 {@link SysUserRoleApi}）。
 */
@Service
@RequiredArgsConstructor
public class SysUserRoleServiceImpl implements SysUserRoleApi {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                if (roleMapper.selectById(roleId) == null) {
                    throw new BizException(SysErrorCode.ROLE_NOT_FOUND);
                }
            }
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    @Override
    public List<Long> listRoleIds(Long userId) {
        return userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeByUser(Long userId) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
    }
}
