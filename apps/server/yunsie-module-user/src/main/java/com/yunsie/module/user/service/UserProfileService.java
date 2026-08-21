package com.yunsie.module.user.service;

import com.yunsie.module.user.dto.ProfileUpsertReq;
import com.yunsie.module.user.vo.ProfileVO;

/**
 * 用户基础资料（user 域内部服务）。
 * 数据范围：本人或「全部数据」范围，否则拒绝（横向越权防护）。
 */
public interface UserProfileService {

    ProfileVO get(Long currentUserId, Long targetUserId);

    void upsert(Long currentUserId, Long targetUserId, ProfileUpsertReq req);

    void removeByUser(Long userId);
}
