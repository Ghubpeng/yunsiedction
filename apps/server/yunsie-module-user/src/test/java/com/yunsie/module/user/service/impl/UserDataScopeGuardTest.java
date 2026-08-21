package com.yunsie.module.user.service.impl;

import com.yunsie.common.error.PermissionErrorCode;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.sys.api.SysAuthApi;
import com.yunsie.module.sys.api.SysUserRoleApi;
import com.yunsie.module.user.entity.UserAccount;
import com.yunsie.module.user.enums.UserStatus;
import com.yunsie.module.user.mapper.UserAccountMapper;
import com.yunsie.module.user.service.AuthService;
import com.yunsie.module.user.service.UserCredentialService;
import com.yunsie.module.user.service.UserProfileService;
import com.yunsie.module.user.vo.UserVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 数据范围守卫单元测试：普通用户只能访问本人数据（横向越权防护）。
 */
class UserDataScopeGuardTest {

    private UserServiceImpl service(SysAuthApi sysAuthApi, UserAccountMapper accountMapper) {
        return new UserServiceImpl(accountMapper,
                mock(UserCredentialService.class),
                mock(UserProfileService.class),
                mock(SysUserRoleApi.class),
                sysAuthApi,
                mock(AuthService.class));
    }

    private UserAccount account(Long id) {
        UserAccount a = new UserAccount();
        a.setId(id);
        a.setUsername("user" + id);
        a.setUserType(1);
        a.setStatus(UserStatus.NORMAL.code());
        return a;
    }

    @Test
    void selfAccess_allowed() {
        UserAccountMapper accountMapper = mock(UserAccountMapper.class);
        when(accountMapper.selectById(10L)).thenReturn(account(10L));
        SysAuthApi sysAuthApi = mock(SysAuthApi.class);
        when(sysAuthApi.hasAllData(10L)).thenReturn(false);

        UserVO vo = service(sysAuthApi, accountMapper).get(10L, 10L);
        assertEquals(10L, vo.id());
    }

    @Test
    void otherUserAccess_denied_20002() {
        UserAccountMapper accountMapper = mock(UserAccountMapper.class);
        when(accountMapper.selectById(20L)).thenReturn(account(20L));
        SysAuthApi sysAuthApi = mock(SysAuthApi.class);
        when(sysAuthApi.hasAllData(10L)).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> service(sysAuthApi, accountMapper).get(10L, 20L));
        assertEquals(PermissionErrorCode.FORBIDDEN_ACCESS.code(), ex.getCode());
    }

    @Test
    void allDataScope_allowsOtherUser() {
        UserAccountMapper accountMapper = mock(UserAccountMapper.class);
        when(accountMapper.selectById(20L)).thenReturn(account(20L));
        SysAuthApi sysAuthApi = mock(SysAuthApi.class);
        when(sysAuthApi.hasAllData(10L)).thenReturn(true);

        UserVO vo = service(sysAuthApi, accountMapper).get(10L, 20L);
        assertEquals(20L, vo.id());
    }
}
