package com.yunsie.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.user.entity.UserCredential;
import com.yunsie.module.user.mapper.UserCredentialMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录凭证单元测试：密码只存 BCrypt 哈希，绝不落明文（安全铁律）。
 */
class UserCredentialServiceTest {

    @Test
    void setPassword_storesBcryptHash_neverPlaintext() {
        UserCredentialMapper mapper = mock(UserCredentialMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        UserCredentialServiceImpl service = new UserCredentialServiceImpl(mapper);

        service.setPassword(1L, "Test@1234");

        ArgumentCaptor<UserCredential> captor = ArgumentCaptor.forClass(UserCredential.class);
        verify(mapper).insert(captor.capture());
        String secret = captor.getValue().getSecret();
        assertNotEquals("Test@1234", secret);
        assertTrue(secret.startsWith("$2"), "secret 必须是 BCrypt 哈希");
    }

    @Test
    void verifyPassword_matchesAgainstHash() {
        UserCredentialMapper mapper = mock(UserCredentialMapper.class);
        UserCredential credential = new UserCredential();
        credential.setSecret(new BCryptPasswordEncoder().encode("Pass@123"));
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(credential);
        UserCredentialServiceImpl service = new UserCredentialServiceImpl(mapper);

        assertTrue(service.verifyPassword(1L, "Pass@123"));
        assertFalse(service.verifyPassword(1L, "wrong-password"));
    }
}
