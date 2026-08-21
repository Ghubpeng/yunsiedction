package com.yunsie.module.user.controller;

import com.yunsie.common.api.Result;
import com.yunsie.common.security.CurrentUser;
import com.yunsie.module.user.dto.ProfileUpsertReq;
import com.yunsie.module.user.service.UserProfileService;
import com.yunsie.module.user.vo.ProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户基础资料接口（/api/v1/user/profiles）。
 * 无权限点要求（登录即可用），数据范围在服务层守卫：
 * 本人数据或「全部数据」范围；修改 URL 中的 userId 访问他人 → 403 + 20002。
 */
@RestController
@RequestMapping("/api/v1/user/profiles")
@RequiredArgsConstructor
@Validated
public class UserProfileController {

    private final UserProfileService profileService;

    @GetMapping("/{userId}")
    public Result<ProfileVO> get(@CurrentUser Long currentUserId, @PathVariable Long userId) {
        return Result.ok(profileService.get(currentUserId, userId));
    }

    @PutMapping("/{userId}")
    public Result<Void> upsert(@CurrentUser Long currentUserId, @PathVariable Long userId,
                               @Valid @RequestBody ProfileUpsertReq req) {
        profileService.upsert(currentUserId, userId, req);
        return Result.ok(null);
    }
}
