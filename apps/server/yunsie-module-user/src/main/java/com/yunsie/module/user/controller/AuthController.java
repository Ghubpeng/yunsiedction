package com.yunsie.module.user.controller;

import com.yunsie.common.api.Result;
import com.yunsie.common.security.CurrentUser;
import com.yunsie.module.user.dto.LoginReq;
import com.yunsie.module.user.dto.LogoutReq;
import com.yunsie.module.user.dto.ProfileUpsertReq;
import com.yunsie.module.user.dto.RefreshReq;
import com.yunsie.module.user.service.AuthService;
import com.yunsie.module.user.service.UserProfileService;
import com.yunsie.module.user.service.UserService;
import com.yunsie.module.user.vo.LoginVO;
import com.yunsie.module.user.vo.ProfileVO;
import com.yunsie.module.user.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证与当前用户接口（/api/v1/user/auth）。
 * login/refresh 为公开接口（SecurityConfig 明确放行）；其余默认需要认证。
 */
@RestController
@RequestMapping("/api/v1/user/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final UserProfileService profileService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginReq req) {
        return Result.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody RefreshReq req) {
        return Result.ok(authService.refresh(req.refreshToken()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@CurrentUser Long userId, @Valid @RequestBody LogoutReq req) {
        authService.logout(userId, req.refreshToken());
        return Result.ok(null);
    }

    /** 当前用户信息（仅本人数据，无横向越权面） */
    @GetMapping("/me")
    public Result<UserVO> me(@CurrentUser Long userId) {
        return Result.ok(userService.get(userId, userId));
    }

    /** 更新本人资料（数据范围：仅本人） */
    @PutMapping("/me/profile")
    public Result<Void> updateMyProfile(@CurrentUser Long userId, @Valid @RequestBody ProfileUpsertReq req) {
        profileService.upsert(userId, userId, req);
        return Result.ok(null);
    }

    /** 本人资料（数据范围：仅本人） */
    @GetMapping("/me/profile")
    public Result<ProfileVO> myProfile(@CurrentUser Long userId) {
        return Result.ok(profileService.get(userId, userId));
    }
}
