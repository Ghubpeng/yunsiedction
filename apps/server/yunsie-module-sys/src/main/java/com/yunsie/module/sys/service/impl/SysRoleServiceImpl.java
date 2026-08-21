package com.yunsie.module.sys.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunsie.common.api.PageResult;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.sys.dto.RoleCreateReq;
import com.yunsie.module.sys.dto.RoleDataScopeReq;
import com.yunsie.module.sys.dto.RoleUpdateReq;
import com.yunsie.module.sys.entity.SysDataScope;
import com.yunsie.module.sys.entity.SysPermission;
import com.yunsie.module.sys.entity.SysRole;
import com.yunsie.module.sys.entity.SysRolePermission;
import com.yunsie.module.sys.entity.SysUserRole;
import com.yunsie.module.sys.error.SysErrorCode;
import com.yunsie.module.sys.mapper.SysDataScopeMapper;
import com.yunsie.module.sys.mapper.SysPermissionMapper;
import com.yunsie.module.sys.mapper.SysRoleMapper;
import com.yunsie.module.sys.mapper.SysRolePermissionMapper;
import com.yunsie.module.sys.mapper.SysUserRoleMapper;
import com.yunsie.module.sys.service.SysRoleService;
import com.yunsie.module.sys.vo.DataScopeVO;
import com.yunsie.module.sys.vo.RoleDetailVO;
import com.yunsie.module.sys.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 角色管理实现。写操作全部事务；系统内置角色（role_type=1）禁止删除。
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysDataScopeMapper dataScopeMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RoleCreateReq req) {
        long count = roleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, req.roleCode()));
        if (count > 0) {
            throw new BizException(SysErrorCode.ROLE_CODE_EXISTS);
        }
        SysRole role = new SysRole();
        role.setRoleCode(req.roleCode());
        role.setRoleName(req.roleName());
        role.setRoleType(req.roleType() == null ? 2 : req.roleType());
        role.setStatus(req.status() == null ? 1 : req.status());
        role.setRemark(req.remark() == null ? "" : req.remark());
        role.setSort(req.sort() == null ? 0 : req.sort());
        roleMapper.insert(role);
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RoleUpdateReq req) {
        SysRole role = requireRole(id);
        role.setRoleName(req.roleName());
        role.setStatus(req.status());
        role.setRemark(req.remark());
        role.setSort(req.sort());
        roleMapper.updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysRole role = requireRole(id);
        if (role.getRoleType() != null && role.getRoleType() == 1) {
            throw new BizException(SysErrorCode.ROLE_PROTECTED);
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
        dataScopeMapper.delete(new LambdaQueryWrapper<SysDataScope>().eq(SysDataScope::getRoleId, id));
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        roleMapper.deleteById(id);
    }

    @Override
    public PageResult<RoleVO> page(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysRole::getRoleName, keyword).or().like(SysRole::getRoleCode, keyword));
        }
        wrapper.orderByAsc(SysRole::getSort).orderByAsc(SysRole::getId);
        Page<SysRole> page = roleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<RoleVO> list = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(list, page.getTotal());
    }

    @Override
    public RoleDetailVO detail(Long id) {
        SysRole role = requireRole(id);
        List<Long> permissionIds = rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id))
                .stream().map(SysRolePermission::getPermissionId).toList();
        List<DataScopeVO> scopes = dataScopeMapper.selectList(
                        new LambdaQueryWrapper<SysDataScope>().eq(SysDataScope::getRoleId, id))
                .stream()
                .map(s -> new DataScopeVO(s.getScopeType(), s.getResourceType(), s.getResourceId()))
                .toList();
        return new RoleDetailVO(toVO(role), permissionIds, scopes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        requireRole(roleId);
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        for (Long permissionId : permissionIds) {
            if (permissionMapper.selectById(permissionId) == null) {
                throw new BizException(SysErrorCode.PERMISSION_NOT_FOUND);
            }
        }
        for (Long permissionId : permissionIds) {
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(permissionId);
            rolePermissionMapper.insert(rp);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignDataScope(Long roleId, RoleDataScopeReq req) {
        requireRole(roleId);
        dataScopeMapper.delete(new LambdaQueryWrapper<SysDataScope>().eq(SysDataScope::getRoleId, roleId));
        if (req.scopeType() == 1) {
            SysDataScope scope = new SysDataScope();
            scope.setRoleId(roleId);
            scope.setScopeType(1);
            scope.setResourceType("*");
            dataScopeMapper.insert(scope);
            return;
        }
        if (req.scopes() == null || req.scopes().isEmpty()) {
            throw new BizException(SysErrorCode.DATA_SCOPE_INVALID);
        }
        for (RoleDataScopeReq.ScopeItem item : req.scopes()) {
            SysDataScope scope = new SysDataScope();
            scope.setRoleId(roleId);
            scope.setScopeType(2);
            scope.setResourceType(item.resourceType());
            scope.setResourceId(item.resourceId());
            dataScopeMapper.insert(scope);
        }
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(SysErrorCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    private RoleVO toVO(SysRole r) {
        return new RoleVO(r.getId(), r.getRoleCode(), r.getRoleName(), r.getRoleType(),
                r.getStatus(), r.getRemark(), r.getSort(), r.getCreateTime());
    }
}
