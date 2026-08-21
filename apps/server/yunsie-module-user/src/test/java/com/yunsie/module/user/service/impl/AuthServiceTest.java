package com.yunsie.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.error.AuthErrorCode;
import com.yunsie.common.exception.BizException;
import com.yunsie.common.security.TokenService;
import com.yunsie.module.sys.api.SysAuthApi;
import com.yunsie.module.user.dto.LoginReq;
import com.yunsie.module.user.entity.UserAccount;
import com.yunsie.module.user.entity.UserRefreshToken;
import com.yunsie.module.user.enums.UserStatus;
import com.yunsie.module.user.mapper.UserAccountMapper;
import com.yunsie.module.user.mapper.UserRefreshTokenMapper;
import com.yunsie.module.user.service.UserCredentialService;
import com.yunsie.module.user.vo.LoginVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务单元测试：登录成功/密码错误/用户不存在/账号禁用/刷新轮换。
 */
class AuthServiceTest {

    private static final TokenService TOKEN_SERVICE =
            new TokenService("auth-unit-test-secret-0123456789abcdef", 7200, 2592000);

    private UserAccount account(Long id, int status) {
        UserAccount a = new UserAccount();
        a.setId(id);
        a.setUsername("alice");
        a.setNickname("alice");
        a.setUserType(1);
        a.setStatus(status);
        return a;
    }

    private AuthServiceImpl service(UserAccountMapper accountMapper, UserCredentialService credentialService,
                                    UserRefreshTokenMapper refreshTokenMapper, SysAuthApi sysAuthApi) {
        return new AuthServiceImpl(accountMapper, credentialService, refreshTokenMapper, TOKEN_SERVICE, sysAuthApi);
    }

    @Test
    void login_success_issuesTokensAndUpdatesLastLogin() {
        UserAccountMapper accountMapper = mock(UserAccountMapper.class);
        UserCredentialService credentialService = mock(UserCredentialService.class);
        UserRefreshTokenMapper refreshTokenMapper = mock(UserRefreshTokenMapper.class);
        SysAuthApi sysAuthApi = mock(SysAuthApi.class);

        when(accountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(7L, UserStatus.NORMAL.code()));
        when(credentialService.verifyPassword(7L, "Pass@1234")).thenReturn(true);
        when(sysAuthApi.listRoleIds(7L)).thenReturn(List.of());

        LoginVO vo = service(accountMapper, credentialService, refreshTokenMapper, sysAuthApi)
                .login(new LoginReq("alice", "Pass@1234"));

        assertNotNull(vo.accessToken());
        assertNotNull(vo.refreshToken());
        assertEquals(7200, vo.expiresIn());
        verify(accountMapper).updateById(any(UserAccount.class));
        verify(refreshTokenMapper).insert(any(UserRefreshToken.class));
    }

    @Test
    void login_wrongPassword_10003() {
        UserAccountMapper accountMapper = mock(UserAccountMapper.class);
        UserCredentialService credentialService = mock(UserCredentialService.class);
        when(accountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(7L, UserStatus.NORMAL.code()));
        when(credentialService.verifyPassword(7L, "bad")).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> service(accountMapper, credentialService, mock(UserRefreshTokenMapper.class), mock(SysAuthApi.class))
                        .login(new LoginReq("alice", "bad")));
        assertEquals(AuthErrorCode.BAD_CREDENTIALS.code(), ex.getCode());
    }

    @Test
    void login_unknownUser_10003_unified() {
        UserAccountMapper accountMapper = mock(UserAccountMapper.class);
        when(accountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service(accountMapper, mock(UserCredentialService.class), mock(UserRefreshTokenMapper.class), mock(SysAuthApi.class))
                        .login(new LoginReq("nobody", "whatever")));
        assertEquals(AuthErrorCode.BAD_CREDENTIALS.code(), ex.getCode());
    }

    @Test
    void login_disabledAccount_10004() {
        UserAccountMapper accountMapper = mock(UserAccountMapper.class);
        when(accountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(7L, UserStatus.DISABLED.code()));

        BizException ex = assertThrows(BizException.class,
                () -> service(accountMapper, mock(UserCredentialService.class), mock(UserRefreshTokenMapper.class), mock(SysAuthApi.class))
                        .login(new LoginReq("alice", "Pass@1234")));
        assertEquals(AuthErrorCode.ACCOUNT_DISABLED.code(), ex.getCode());
        verify(mock(UserCredentialService.class), never()).verifyPassword(any(), any());
    }

    @Test
    void refresh_revokedToken_10002() {
        UserRefreshToken stored = new UserRefreshToken();
        stored.setUserId(7L);
        stored.setRevoked(1);
        stored.setExpiresAt(LocalDateTime.now().plusDays(1));
        UserRefreshTokenMapper refreshTokenMapper = mock(UserRefreshTokenMapper.class);
        when(refreshTokenMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stored);

        BizException ex = assertThrows(BizException.class,
                () -> service(mock(UserAccountMapper.class), mock(UserCredentialService.class), refreshTokenMapper, mock(SysAuthApi.class))
                        .refresh("some-refresh-token"));
        assertEquals(AuthErrorCode.TOKEN_INVALID.code(), ex.getCode());
    }

    @Test
    void refresh_validToken_rotatesOldToken() {
        UserRefreshToken stored = new UserRefreshToken();
        stored.setId(9L);
        stored.setUserId(7L);
        stored.setRevoked(0);
        stored.setExpiresAt(LocalDateTime.now().plusDays(1));
        UserRefreshTokenMapper refreshTokenMapper = mock(UserRefreshTokenMapper.class);
        when(refreshTokenMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stored);
        UserAccountMapper accountMapper = mock(UserAccountMapper.class);
        when(accountMapper.selectById(7L)).thenReturn(account(7L, UserStatus.NORMAL.code()));
        SysAuthApi sysAuthApi = mock(SysAuthApi.class);
        when(sysAuthApi.listRoleIds(7L)).thenReturn(List.of());

        LoginVO vo = service(accountMapper, mock(UserCredentialService.class), refreshTokenMapper, sysAuthApi)
                .refresh("some-refresh-token");

        assertNotNull(vo.accessToken());
        ArgumentCaptor<UserRefreshToken> captor = ArgumentCaptor.forClass(UserRefreshToken.class);
        verify(refreshTokenMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getRevoked(), "旧 refresh token 必须被撤销（轮换）");
    }

    @Test
    void refresh_reusedOldTokenWithinGrace_returnsSamePair() {
        // 丢响应重试：服务端已轮换但响应丢失，客户端用同一旧 token 重试 → 宽限期内幂等返回同一令牌对
        UserRefreshToken stored = new UserRefreshToken();
        stored.setId(9L);
        stored.setUserId(7L);
        stored.setRevoked(0);
        stored.setExpiresAt(LocalDateTime.now().plusDays(1));
        UserRefreshTokenMapper refreshTokenMapper = mock(UserRefreshTokenMapper.class);
        when(refreshTokenMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stored);
        UserAccountMapper accountMapper = mock(UserAccountMapper.class);
        when(accountMapper.selectById(7L)).thenReturn(account(7L, UserStatus.NORMAL.code()));
        SysAuthApi sysAuthApi = mock(SysAuthApi.class);
        when(sysAuthApi.listRoleIds(7L)).thenReturn(List.of());

        AuthServiceImpl svc = service(accountMapper, mock(UserCredentialService.class), refreshTokenMapper, sysAuthApi);
        LoginVO first = svc.refresh("some-refresh-token");
        // 第一次轮换后 stored 已被置为 revoked=1；第二次请求同一旧 token → 幂等窗口命中
        LoginVO second = svc.refresh("some-refresh-token");

        assertEquals(first.accessToken(), second.accessToken(), "宽限内重试必须返回同一 access token（幂等）");
        assertEquals(first.refreshToken(), second.refreshToken(), "宽限内重试必须返回同一 refresh token（不产生新轮换）");
        verify(refreshTokenMapper).updateById(any(UserRefreshToken.class));
    }

    @Test
    void refresh_revokedToken_withoutRotationCache_rejected() {
        // 已撤销且无幂等窗口条目（真实重放/宽限外）→ 严格拒绝
        UserRefreshToken stored = new UserRefreshToken();
        stored.setId(9L);
        stored.setUserId(7L);
        stored.setRevoked(1);
        stored.setExpiresAt(LocalDateTime.now().plusDays(1));
        UserRefreshTokenMapper refreshTokenMapper = mock(UserRefreshTokenMapper.class);
        when(refreshTokenMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stored);

        BizException ex = assertThrows(BizException.class,
                () -> service(mock(UserAccountMapper.class), mock(UserCredentialService.class), refreshTokenMapper, mock(SysAuthApi.class))
                        .refresh("old-refresh-token"));
        assertEquals(AuthErrorCode.TOKEN_INVALID.code(), ex.getCode(), "宽限外/无窗口的重放必须拒绝");
    }
}
