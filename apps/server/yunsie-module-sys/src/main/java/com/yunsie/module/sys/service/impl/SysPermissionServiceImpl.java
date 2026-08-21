package com.yunsie.module.sys.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.sys.dto.PermissionCreateReq;
import com.yunsie.module.sys.dto.PermissionUpdateReq;
import com.yunsie.module.sys.entity.SysPermission;
import com.yunsie.module.sys.entity.SysRolePermission;
import com.yunsie.module.sys.error.SysErrorCode;
import com.yunsie.module.sys.mapper.SysPermissionMapper;
import com.yunsie.module.sys.mapper.SysRolePermissionMapper;
import com.yunsie.module.sys.service.SysPermissionService;
import com.yunsie.module.sys.vo.PermissionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权限管理实现。
 */
@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl implements SysPermissionService {

    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PermissionCreateReq req) {
        long count = permissionMapper.selectCount(
                new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getPermissionCode, req.permissionCode()));
        if (count > 0) {
            throw new BizException(SysErrorCode.PERMISSION_CODE_EXISTS);
        }
        SysPermission p = new SysPermission();
        p.setParentId(req.parentId() == null ? 0L : req.parentId());
        p.setPermissionCode(req.permissionCode());
        p.setPermissionName(req.permissionName());
        p.setPermType(req.permType());
        p.setPath(req.path() == null ? "" : req.path());
        p.setIcon(req.icon() == null ? "" : req.icon());
        p.setSort(req.sort() == null ? 0 : req.sort());
        p.setStatus(req.status() == null ? 1 : req.status());
        p.setRemark(req.remark() == null ? "" : req.remark());
        permissionMapper.insert(p);
        return p.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PermissionUpdateReq req) {
        SysPermission p = requirePermission(id);
        p.setPermissionName(req.permissionName());
        p.setPath(req.path());
        p.setIcon(req.icon());
        p.setSort(req.sort());
        p.setStatus(req.status());
        p.setRemark(req.remark());
        permissionMapper.updateById(p);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requirePermission(id);
        long children = permissionMapper.selectCount(
                new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getParentId, id));
        if (children > 0) {
            throw new BizException(SysErrorCode.PERMISSION_HAS_CHILDREN);
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getPermissionId, id));
        permissionMapper.deleteById(id);
    }

    @Override
    public PermissionVO detail(Long id) {
        return toVO(requirePermission(id), List.of());
    }

    @Override
    public List<PermissionVO> tree() {
        List<SysPermission> all = permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getSort).orderByAsc(SysPermission::getId));
        Map<Long, List<SysPermission>> byParent = all.stream()
                .collect(Collectors.groupingBy(SysPermission::getParentId));
        return buildTree(0L, byParent);
    }

    private List<PermissionVO> buildTree(Long parentId, Map<Long, List<SysPermission>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(p -> toVO(p, buildTree(p.getId(), byParent)))
                .toList();
    }

    private SysPermission requirePermission(Long id) {
        SysPermission p = permissionMapper.selectById(id);
        if (p == null) {
            throw new BizException(SysErrorCode.PERMISSION_NOT_FOUND);
        }
        return p;
    }

    private PermissionVO toVO(SysPermission p, List<PermissionVO> children) {
        return new PermissionVO(p.getId(), p.getParentId(), p.getPermissionCode(), p.getPermissionName(),
                p.getPermType(), p.getPath(), p.getIcon(), p.getSort(), p.getStatus(), p.getRemark(), children);
    }
}
