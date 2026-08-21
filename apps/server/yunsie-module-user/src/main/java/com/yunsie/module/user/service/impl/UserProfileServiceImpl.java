package com.yunsie.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.error.PermissionErrorCode;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.sys.api.SysAuthApi;
import com.yunsie.module.user.dto.ProfileUpsertReq;
import com.yunsie.module.user.entity.UserProfile;
import com.yunsie.module.user.mapper.UserProfileMapper;
import com.yunsie.module.user.service.UserProfileService;
import com.yunsie.module.user.vo.ProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户基础资料实现（含数据范围守卫）。
 */
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileMapper profileMapper;
    private final SysAuthApi sysAuthApi;

    @Override
    public ProfileVO get(Long currentUserId, Long targetUserId) {
        checkDataAccess(currentUserId, targetUserId);
        UserProfile profile = find(targetUserId);
        if (profile == null) {
            return null;
        }
        return new ProfileVO(profile.getUserId(), profile.getRealName(), profile.getGender(),
                profile.getBirthday(), profile.getEmail());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsert(Long currentUserId, Long targetUserId, ProfileUpsertReq req) {
        checkDataAccess(currentUserId, targetUserId);
        UserProfile profile = find(targetUserId);
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(targetUserId);
        }
        profile.setRealName(req.realName() == null ? "" : req.realName());
        profile.setGender(req.gender() == null ? 0 : req.gender());
        profile.setBirthday(req.birthday());
        profile.setEmail(req.email() == null ? "" : req.email());
        if (profile.getId() == null) {
            profileMapper.insert(profile);
        } else {
            profileMapper.updateById(profile);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeByUser(Long userId) {
        profileMapper.delete(new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId));
    }

    /** 数据范围守卫：本人或「全部数据」；否则 20002 */
    private void checkDataAccess(Long currentUserId, Long targetUserId) {
        if (currentUserId != null && currentUserId.equals(targetUserId)) {
            return;
        }
        if (sysAuthApi.hasAllData(currentUserId)) {
            return;
        }
        throw new BizException(PermissionErrorCode.FORBIDDEN_ACCESS);
    }

    private UserProfile find(Long userId) {
        return profileMapper.selectOne(new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId));
    }
}
