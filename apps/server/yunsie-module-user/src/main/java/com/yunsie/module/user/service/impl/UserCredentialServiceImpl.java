package com.yunsie.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.user.entity.UserCredential;
import com.yunsie.module.user.enums.CredentialType;
import com.yunsie.module.user.mapper.UserCredentialMapper;
import com.yunsie.module.user.service.UserCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录凭证实现。密码一律 BCrypt 哈希后入库，任何路径不落明文。
 */
@Service
@RequiredArgsConstructor
public class UserCredentialServiceImpl implements UserCredentialService {

    private final UserCredentialMapper credentialMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPassword(Long userId, String rawPassword) {
        String hash = passwordEncoder.encode(rawPassword);
        UserCredential existing = findPasswordCredential(userId);
        if (existing == null) {
            UserCredential credential = new UserCredential();
            credential.setUserId(userId);
            credential.setCredentialType(CredentialType.PASSWORD.code());
            credential.setSecret(hash);
            credential.setStatus(1);
            credential.setRemark("");
            credentialMapper.insert(credential);
        } else {
            existing.setSecret(hash);
            credentialMapper.updateById(existing);
        }
    }

    @Override
    public boolean verifyPassword(Long userId, String rawPassword) {
        UserCredential credential = findPasswordCredential(userId);
        if (credential == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, credential.getSecret());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeByUser(Long userId) {
        credentialMapper.delete(new LambdaQueryWrapper<UserCredential>().eq(UserCredential::getUserId, userId));
    }

    private UserCredential findPasswordCredential(Long userId) {
        return credentialMapper.selectOne(new LambdaQueryWrapper<UserCredential>()
                .eq(UserCredential::getUserId, userId)
                .eq(UserCredential::getCredentialType, CredentialType.PASSWORD.code()));
    }
}
