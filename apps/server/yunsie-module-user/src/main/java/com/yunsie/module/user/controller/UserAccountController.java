package com.yunsie.module.user.controller;

import com.yunsie.common.api.PageResult;
import com.yunsie.common.api.Result;
import com.yunsie.common.security.CurrentUser;
import com.yunsie.module.user.dto.UserCreateReq;
import com.yunsie.module.user.dto.UserPasswordResetReq;
import com.yunsie.module.user.dto.UserRolesReq;
import com.yunsie.module.user.dto.UserStatusReq;
import com.yunsie.module.user.dto.UserUpdateReq;
import com.yunsie.module.user.service.UserService;
import com.yunsie.module.user.vo.UserVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户账号管理接口（/api/v1/user/accounts）。
 * 权限点：@PreAuthorize("@perm.has('sys:user:*')")（统一机制，禁止手写角色判断）；
 * 数据范围：服务层守卫（全部数据 or 本人）。
 */
@RestController
@RequestMapping("/api/v1/user/accounts")
@RequiredArgsConstructor
@Validated
public class UserAccountController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("@perm.has('sys:user:create')")
    public Result<Long> create(@Valid @RequestBody UserCreateReq req) {
        return Result.ok(userService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:user:update')")
    public Result<Void> update(@CurrentUser Long currentUserId, @PathVariable Long id,
                               @Valid @RequestBody UserUpdateReq req) {
        userService.update(currentUserId, id, req);
        return Result.ok(null);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@perm.has('sys:user:update')")
    public Result<Void> updateStatus(@CurrentUser Long currentUserId, @PathVariable Long id,
                                     @Valid @RequestBody UserStatusReq req) {
        userService.updateStatus(currentUserId, id, req.status());
        return Result.ok(null);
    }

    @PostMapping("/{id}/password")
    @PreAuthorize("@perm.has('sys:user:password')")
    public Result<Void> resetPassword(@CurrentUser Long currentUserId, @PathVariable Long id,
                                      @Valid @RequestBody UserPasswordResetReq req) {
        userService.resetPassword(currentUserId, id, req.newPassword());
        return Result.ok(null);
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("@perm.has('sys:user:roles')")
    public Result<Void> assignRoles(@CurrentUser Long currentUserId, @PathVariable Long id,
                                    @Valid @RequestBody UserRolesReq req) {
        userService.assignRoles(currentUserId, id, req.roleIds());
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:user:delete')")
    public Result<Void> delete(@CurrentUser Long currentUserId, @PathVariable Long id) {
        userService.delete(currentUserId, id);
        return Result.ok(null);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('sys:user:view')")
    public Result<UserVO> detail(@CurrentUser Long currentUserId, @PathVariable Long id) {
        return Result.ok(userService.get(currentUserId, id));
    }

    @GetMapping
    @PreAuthorize("@perm.has('sys:user:view')")
    public Result<PageResult<UserVO>> page(
            @CurrentUser Long currentUserId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum 最小为1") Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 最小为1")
            @Max(value = 100, message = "pageSize 最大100") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @Min(value = 0, message = "状态非法")
            @Max(value = 2, message = "状态非法") Integer status) {
        return Result.ok(userService.page(currentUserId, pageNum, pageSize, keyword, status));
    }
}
