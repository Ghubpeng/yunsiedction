package com.yunsie.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunsie.common.api.PageResult;
import com.yunsie.common.error.PermissionErrorCode;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.sys.api.SysAuthApi;
import com.yunsie.module.sys.api.SysUserRoleApi;
import com.yunsie.module.user.dto.UserCreateReq;
import com.yunsie.module.user.dto.UserUpdateReq;
import com.yunsie.module.user.entity.UserAccount;
import com.yunsie.module.user.enums.UserStatus;
import com.yunsie.module.user.enums.UserType;
import com.yunsie.module.user.error.UserErrorCode;
import com.yunsie.module.user.mapper.UserAccountMapper;
import com.yunsie.module.user.service.AuthService;
import com.yunsie.module.user.service.UserCredentialService;
import com.yunsie.module.user.service.UserProfileService;
import com.yunsie.module.user.service.UserService;
import com.yunsie.module.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户账号管理实现。
 * 跨域角色/数据范围经 sys 域契约（SysUserRoleApi / SysAuthApi）执行 —— 不直连 sys 表。
 * 数据范围铁律：他人数据仅当拥有「全部数据」范围时可访问，否则 20002（横向越权防护）。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserAccountMapper accountMapper;
    private final UserCredentialService credentialService;
    private final UserProfileService profileService;
    private final SysUserRoleApi sysUserRoleApi;
    private final SysAuthApi sysAuthApi;
    private final AuthService authService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserCreateReq req) {
        checkUsernameUnique(req.username(), null);
        if (StringUtils.hasText(req.mobile())) {
            checkMobileUnique(req.mobile(), null);
        }
        UserAccount account = new UserAccount();
        account.setUsername(req.username());
        account.setMobile(req.mobile());
        account.setNickname(req.nickname() == null ? "" : req.nickname());
        account.setUserType(req.userType() == null ? UserType.STUDENT.code() : req.userType());
        account.setStatus(UserStatus.NORMAL.code());
        accountMapper.insert(account);

        credentialService.setPassword(account.getId(), req.password());

        if (req.roleIds() != null && !req.roleIds().isEmpty()) {
            sysUserRoleApi.assignRoles(account.getId(), req.roleIds());
        }
        return account.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long currentUserId, Long id, UserUpdateReq req) {
        UserAccount account = requireAccount(id);
        checkUserDataAccess(currentUserId, id);
        if (StringUtils.hasText(req.mobile())) {
            checkMobileUnique(req.mobile(), id);
        }
        account.setNickname(req.nickname());
        account.setAvatar(req.avatar());
        account.setMobile(req.mobile());
        account.setUserType(req.userType());
        accountMapper.updateById(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long currentUserId, Long id, int status) {
        UserAccount account = requireAccount(id);
        checkUserDataAccess(currentUserId, id);
        if (UserStatus.of(status) == null) {
            throw new BizException(UserErrorCode.USER_STATUS_INVALID);
        }
        account.setStatus(status);
        accountMapper.updateById(account);
        // 禁用/锁定后立即吊销全部 refresh token（下次请求 access 过期即失效）
        if (status != UserStatus.NORMAL.code()) {
            authService.revokeAllByUser(id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long currentUserId, Long id, String newPassword) {
        requireAccount(id);
        checkUserDataAccess(currentUserId, id);
        credentialService.setPassword(id, newPassword);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long currentUserId, Long id, List<Long> roleIds) {
        requireAccount(id);
        checkUserDataAccess(currentUserId, id);
        sysUserRoleApi.assignRoles(id, roleIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long currentUserId, Long id) {
        requireAccount(id);
        checkUserDataAccess(currentUserId, id);
        accountMapper.deleteById(id);
        profileService.removeByUser(id);
        credentialService.removeByUser(id);
        sysUserRoleApi.removeByUser(id);
        authService.revokeAllByUser(id);
    }

    @Override
    public UserVO get(Long currentUserId, Long id) {
        UserAccount account = requireAccount(id);
        checkUserDataAccess(currentUserId, id);
        return toVO(account);
    }

    @Override
    public PageResult<UserVO> page(Long currentUserId, int pageNum, int pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
        if (!sysAuthApi.hasAllData(currentUserId)) {
            // 数据范围：无「全部数据」权限时只能看到本人
            wrapper.eq(UserAccount::getId, currentUserId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(UserAccount::getUsername, keyword)
                    .or().like(UserAccount::getNickname, keyword)
                    .or().like(UserAccount::getMobile, keyword));
        }
        if (status != null) {
            wrapper.eq(UserAccount::getStatus, status);
        }
        wrapper.orderByDesc(UserAccount::getId);
        Page<UserAccount> page = accountMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<UserVO> list = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(list, page.getTotal());
    }

    /**
     * 数据范围守卫：本人数据放行；他人数据仅「全部数据」范围放行；否则 20002。
     * 该守卫与 Controller 权限点校验双保险（防通过修改 URL userId 横向越权）。
     */
    private void checkUserDataAccess(Long currentUserId, Long targetUserId) {
        if (currentUserId != null && currentUserId.equals(targetUserId)) {
            return;
        }
        if (sysAuthApi.hasAllData(currentUserId)) {
            return;
        }
        throw new BizException(PermissionErrorCode.FORBIDDEN_ACCESS);
    }

    private UserAccount requireAccount(Long id) {
        UserAccount account = accountMapper.selectById(id);
        if (account == null) {
            throw new BizException(UserErrorCode.USER_NOT_FOUND);
        }
        return account;
    }

    private void checkUsernameUnique(String username, Long excludeId) {
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username);
        if (excludeId != null) {
            wrapper.ne(UserAccount::getId, excludeId);
        }
        if (accountMapper.selectCount(wrapper) > 0) {
            throw new BizException(UserErrorCode.USERNAME_EXISTS);
        }
    }

    private void checkMobileUnique(String mobile, Long excludeId) {
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getMobile, mobile);
        if (excludeId != null) {
            wrapper.ne(UserAccount::getId, excludeId);
        }
        if (accountMapper.selectCount(wrapper) > 0) {
            throw new BizException(UserErrorCode.MOBILE_EXISTS);
        }
    }

    private UserVO toVO(UserAccount a) {
        return new UserVO(a.getId(), a.getUsername(), a.getMobile(), a.getNickname(), a.getAvatar(),
                a.getUserType(), a.getStatus(), a.getLastLoginTime(), a.getCreateTime(),
                sysUserRoleApi.listRoleIds(a.getId()));
    }
}
