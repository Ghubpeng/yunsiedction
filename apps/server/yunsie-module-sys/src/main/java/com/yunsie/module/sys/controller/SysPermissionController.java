package com.yunsie.module.sys.controller;

import com.yunsie.common.api.Result;
import com.yunsie.common.security.CurrentUser;
import com.yunsie.module.sys.api.SysAuthApi;
import com.yunsie.module.sys.dto.PermissionCreateReq;
import com.yunsie.module.sys.dto.PermissionUpdateReq;
import com.yunsie.module.sys.service.SysPermissionService;
import com.yunsie.module.sys.vo.PermissionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限管理接口（/api/v1/sys/permissions）。
 * 权限控制：统一走 @PreAuthorize("@perm.has('...')")。
 */
@RestController
@RequestMapping("/api/v1/sys/permissions")
@RequiredArgsConstructor
@Validated
public class SysPermissionController {

    private final SysPermissionService permissionService;
    private final SysAuthApi sysAuthApi;

    /**
     * 当前用户权限点编码列表（登录即可；Stage 1.9 追加：管理端菜单/路由按权限渲染）。
     * 权限实时解析自 sys 域（permission-rbac：服务端仍为最终校验）。
     */
    @GetMapping("/me/codes")
    public Result<List<String>> myCodes(@CurrentUser Long userId) {
        return Result.ok(sysAuthApi.listPermissionCodes(userId));
    }

    @PostMapping
    @PreAuthorize("@perm.has('sys:permission:create')")
    public Result<Long> create(@Valid @RequestBody PermissionCreateReq req) {
        return Result.ok(permissionService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:permission:update')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PermissionUpdateReq req) {
        permissionService.update(id, req);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:permission:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return Result.ok(null);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('sys:permission:view')")
    public Result<PermissionVO> detail(@PathVariable Long id) {
        return Result.ok(permissionService.detail(id));
    }

    @GetMapping("/tree")
    @PreAuthorize("@perm.has('sys:permission:view')")
    public Result<List<PermissionVO>> tree() {
        return Result.ok(permissionService.tree());
    }
}
