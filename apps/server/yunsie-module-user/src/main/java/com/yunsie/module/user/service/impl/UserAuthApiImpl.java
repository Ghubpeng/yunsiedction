package com.yunsie.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.user.api.UserAuthApi;
import com.yunsie.module.user.entity.UserAccount;
import com.yunsie.module.user.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户认证信息契约实现。
 */
@Service
@RequiredArgsConstructor
public class UserAuthApiImpl implements UserAuthApi {

    private final UserAccountMapper accountMapper;

    @Override
    public UserAuthView findByAccount(String account) {
        UserAccount byUsername = accountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, account));
        if (byUsername != null) {
            return toView(byUsername);
        }
        UserAccount byMobile = accountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getMobile, account));
        return byMobile == null ? null : toView(byMobile);
    }

    @Override
    public UserAuthView findById(Long userId) {
        UserAccount account = accountMapper.selectById(userId);
        return account == null ? null : toView(account);
    }

    private UserAuthView toView(UserAccount a) {
        return new UserAuthView(a.getId(), a.getUsername(), a.getMobile(), a.getNickname(),
                a.getUserType(), a.getStatus());
    }
}
