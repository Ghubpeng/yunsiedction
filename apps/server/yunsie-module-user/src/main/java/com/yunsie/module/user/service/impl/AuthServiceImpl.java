package com.yunsie.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.error.AuthErrorCode;
import com.yunsie.common.error.CommonErrorCode;
import com.yunsie.common.exception.BizException;
import com.yunsie.common.security.TokenService;
import com.yunsie.module.sys.api.SysAuthApi;
import com.yunsie.module.user.dto.LoginReq;
import com.yunsie.module.user.entity.UserAccount;
import com.yunsie.module.user.entity.UserRefreshToken;
import com.yunsie.module.user.enums.UserStatus;
import com.yunsie.module.user.mapper.UserAccountMapper;
import com.yunsie.module.user.mapper.UserRefreshTokenMapper;
import com.yunsie.module.user.service.AuthService;
import com.yunsie.module.user.service.UserCredentialService;
import com.yunsie.module.user.vo.LoginVO;
import com.yunsie.module.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证服务实现。
 * 安全约定：账号不存在与密码错误统一返回 10003（防账号枚举）；
 * refresh token 仅存 SHA-256 哈希，轮换时旧令牌作废；
 * 轮换幂等窗口：网络瞬断下服务端已轮换但响应丢失时，客户端会用同一旧 token 重试——
 * 宽限期（ROTATION_GRACE_SECONDS）内幂等返回同一令牌对（不再产生新轮换），
 * 避免"服务端已消耗、客户端未收到"导致误登出；宽限外重放仍严格拒绝（互踢语义保留）。
 * 幂等缓存为进程内内存（单实例部署起步，见实体注释；重启丢失仅退化为严格拒绝）。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 轮换幂等宽限期（秒）；内存条目按此过期 */
    private static final long ROTATION_GRACE_SECONDS = 60;

    /** 旧 token 哈希 → 最近一次轮换签发的令牌对（丢响应重试用） */
    private final Map<String, RotationOutcome> rotationCache = new ConcurrentHashMap<>();

    /** 缓存上限：超限时清理过期条目（防内存膨胀） */
    private static final int ROTATION_CACHE_MAX = 10_000;

    private record RotationOutcome(LoginVO issued, LocalDateTime rotatedAt) {
    }

    private final UserAccountMapper accountMapper;
    private final UserCredentialService credentialService;
    private final UserRefreshTokenMapper refreshTokenMapper;
    private final TokenService tokenService;
    private final SysAuthApi sysAuthApi;

    /** Demo 演示账号（yunsie.demo.username，仅 demo 端点启用时使用） */
    @Value("${yunsie.demo.username:demo_learner}")
    private String demoUsername;

    /** Demo 教师账号（role=teacher） */
    @Value("${yunsie.demo.teacher-username:demo_teacher}")
    private String demoTeacherUsername;

    /** Demo 管理员账号（role=admin，默认指向种子管理员 admin） */
    @Value("${yunsie.demo.admin-username:admin}")
    private String demoAdminUsername;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginReq req) {
        UserAccount account = findByUsername(req.account());
        if (account == null) {
            account = findByMobile(req.account());
        }
        if (account == null) {
            throw new BizException(AuthErrorCode.BAD_CREDENTIALS);
        }
        if (account.getStatus() == null || account.getStatus() != UserStatus.NORMAL.code()) {
            throw new BizException(AuthErrorCode.ACCOUNT_DISABLED);
        }
        if (!credentialService.verifyPassword(account.getId(), req.password())) {
            throw new BizException(AuthErrorCode.BAD_CREDENTIALS);
        }
        account.setLastLoginTime(LocalDateTime.now());
        accountMapper.updateById(account);
        return issueTokens(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO demoLogin(String role) {
        UserAccount account = findByUsername(resolveDemoUsername(role));
        if (account == null) {
            throw new BizException(AuthErrorCode.DEMO_NOT_READY);
        }
        if (account.getStatus() == null || account.getStatus() != UserStatus.NORMAL.code()) {
            throw new BizException(AuthErrorCode.ACCOUNT_DISABLED);
        }
        account.setLastLoginTime(LocalDateTime.now());
        accountMapper.updateById(account);
        return issueTokens(account);
    }

    /** role ∈ learner/teacher/admin；null/空默认 learner；非法 role 抛参数错误（复用 common 码） */
    private String resolveDemoUsername(String role) {
        if (role == null || role.isBlank() || "learner".equals(role)) {
            return demoUsername;
        }
        if ("teacher".equals(role)) {
            return demoTeacherUsername;
        }
        if ("admin".equals(role)) {
            return demoAdminUsername;
        }
        throw new BizException(CommonErrorCode.PARAM_INVALID);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO refresh(String refreshToken) {
        String hash = tokenService.hashRefreshToken(refreshToken);
        UserRefreshToken stored = refreshTokenMapper.selectOne(
                new LambdaQueryWrapper<UserRefreshToken>().eq(UserRefreshToken::getTokenHash, hash));
        if (stored == null) {
            throw new BizException(AuthErrorCode.TOKEN_INVALID);
        }
        if (stored.getRevoked() != null && stored.getRevoked() == 1) {
            // 已撤销：仅当为宽限期内的丢响应重试时幂等返回同一令牌对；否则严格拒绝
            RotationOutcome outcome = rotationCache.get(hash);
            if (outcome != null && outcome.rotatedAt().isAfter(LocalDateTime.now().minusSeconds(ROTATION_GRACE_SECONDS))) {
                return outcome.issued();
            }
            throw new BizException(AuthErrorCode.TOKEN_INVALID);
        }
        if (stored.getExpiresAt() == null || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(AuthErrorCode.TOKEN_INVALID);
        }
        UserAccount account = accountMapper.selectById(stored.getUserId());
        if (account == null) {
            throw new BizException(AuthErrorCode.TOKEN_INVALID);
        }
        if (account.getStatus() == null || account.getStatus() != UserStatus.NORMAL.code()) {
            throw new BizException(AuthErrorCode.ACCOUNT_DISABLED);
        }
        // 轮换：旧令牌作废，签发新令牌对并登记幂等窗口
        stored.setRevoked(1);
        stored.setUpdateTime(LocalDateTime.now());
        refreshTokenMapper.updateById(stored);
        LoginVO issued = issueTokens(account);
        rotationCache.put(hash, new RotationOutcome(issued, LocalDateTime.now()));
        trimRotationCache();
        return issued;
    }

    /** 缓存超限时清理过期条目（懒清理，防止无界增长） */
    private void trimRotationCache() {
        if (rotationCache.size() <= ROTATION_CACHE_MAX) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(ROTATION_GRACE_SECONDS);
        rotationCache.entrySet().removeIf(e -> e.getValue().rotatedAt().isBefore(cutoff));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(Long userId, String refreshToken) {
        String hash = tokenService.hashRefreshToken(refreshToken);
        UserRefreshToken stored = refreshTokenMapper.selectOne(
                new LambdaQueryWrapper<UserRefreshToken>().eq(UserRefreshToken::getTokenHash, hash));
        if (stored == null || !stored.getUserId().equals(userId)) {
            return; // 幂等：不存在或不属于当前用户，视为已登出
        }
        stored.setRevoked(1);
        refreshTokenMapper.updateById(stored);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeAllByUser(Long userId) {
        UserRefreshToken update = new UserRefreshToken();
        update.setRevoked(1);
        refreshTokenMapper.update(update, new LambdaQueryWrapper<UserRefreshToken>()
                .eq(UserRefreshToken::getUserId, userId)
                .eq(UserRefreshToken::getRevoked, 0));
    }

    private LoginVO issueTokens(UserAccount account) {
        String accessToken = tokenService.createAccessToken(account.getId(), account.getUsername());
        String refreshToken = tokenService.newRefreshToken();
        UserRefreshToken stored = new UserRefreshToken();
        stored.setUserId(account.getId());
        stored.setTokenHash(tokenService.hashRefreshToken(refreshToken));
        stored.setExpiresAt(LocalDateTime.now().plusSeconds(tokenService.refreshTtlSeconds()));
        stored.setRevoked(0);
        stored.setRemark("");
        refreshTokenMapper.insert(stored);
        return new LoginVO(accessToken, refreshToken, tokenService.accessTtlSeconds(), toUserVO(account));
    }

    private UserVO toUserVO(UserAccount a) {
        return new UserVO(a.getId(), a.getUsername(), a.getMobile(), a.getNickname(), a.getAvatar(),
                a.getUserType(), a.getStatus(), a.getLastLoginTime(), a.getCreateTime(),
                sysAuthApi.listRoleIds(a.getId()));
    }

    private UserAccount findByUsername(String username) {
        return accountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username));
    }

    private UserAccount findByMobile(String mobile) {
        return accountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getMobile, mobile));
    }
}
