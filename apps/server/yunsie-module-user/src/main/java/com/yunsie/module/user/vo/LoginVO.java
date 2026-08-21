package com.yunsie.module.user.vo;

/**
 * 登录/刷新结果 VO。
 * 说明：不携带权限列表 —— 权限由服务端每次请求实时解析（permission-rbac）。
 */
public record LoginVO(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserVO user) {
}
