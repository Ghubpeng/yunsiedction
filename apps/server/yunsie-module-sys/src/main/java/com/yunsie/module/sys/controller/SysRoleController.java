package com.yunsie.module.sys.controller;

import com.yunsie.common.api.PageResult;
import com.yunsie.common.api.Result;
import com.yunsie.module.sys.dto.RoleCreateReq;
import com.yunsie.module.sys.dto.RoleDataScopeReq;
import com.yunsie.module.sys.dto.RolePermissionsReq;
import com.yunsie.module.sys.dto.RoleUpdateReq;
import com.yunsie.module.sys.service.SysRoleService;
import com.yunsie.module.sys.vo.RoleDetailVO;
import com.yunsie.module.sys.vo.RoleVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色管理接口（/api/v1/sys/roles）。
 * 权限控制：统一走 @PreAuthorize("@perm.has('...')")（PermissionChecker），
 * 通配 *:*:* 恒通过；禁止在业务代码手写角色判断（permission-rbac）。
 */
@RestController
@RequestMapping("/api/v1/sys/roles")
@RequiredArgsConstructor
@Validated
public class SysRoleController {

    private final SysRoleService roleService;

    @PostMapping
    @PreAuthorize("@perm.has('sys:role:create')")
    public Result<Long> create(@Valid @RequestBody RoleCreateReq req) {
        return Result.ok(roleService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:role:update')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RoleUpdateReq req) {
        roleService.update(id, req);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:role:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.ok(null);
    }

    @GetMapping
    @PreAuthorize("@perm.has('sys:role:view')")
    public Result<PageResult<RoleVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum 最小为1") Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 最小为1")
            @Max(value = 100, message = "pageSize 最大100") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.ok(roleService.page(pageNum, pageSize, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('sys:role:view')")
    public Result<RoleDetailVO> detail(@PathVariable Long id) {
        return Result.ok(roleService.detail(id));
    }

    @PostMapping("/{id}/permissions")
    @PreAuthorize("@perm.has('sys:role:assign')")
    public Result<Void> assignPermissions(@PathVariable Long id, @Valid @RequestBody RolePermissionsReq req) {
        roleService.assignPermissions(id, req.permissionIds());
        return Result.ok(null);
    }

    @PostMapping("/{id}/data-scopes")
    @PreAuthorize("@perm.has('sys:role:assign')")
    public Result<Void> assignDataScope(@PathVariable Long id, @Valid @RequestBody RoleDataScopeReq req) {
        roleService.assignDataScope(id, req);
        return Result.ok(null);
    }
}
