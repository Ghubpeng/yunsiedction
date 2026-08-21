package com.yunsie.module.user.service;

import com.yunsie.module.user.dto.LoginReq;
import com.yunsie.module.user.vo.LoginVO;

/**
 * 认证服务（登录/刷新/登出）。
 * 凭证校验走 UserCredentialService（BCrypt）；Token 设施为 common TokenService；
 * refresh token 仅存 SHA-256 哈希于 user_refresh_token（轮换 + 撤销）。
 */
public interface AuthService {

    LoginVO login(LoginReq req);

    /** 刷新：旧 refresh 作废，签发新 access + refresh（轮换） */
    LoginVO refresh(String refreshToken);

    /**
     * Demo 一键登录（仅 yunsie.demo.enabled=true 时端点存在）：
     * 为预播种的演示账号签发真实令牌（生产默认关闭，鉴权语义不变）。
     *
     * @param role learner/teacher/admin（null 或空按 learner 处理）
     */
    LoginVO demoLogin(String role);

    /** 登出：撤销指定 refresh token（幂等） */
    void logout(Long userId, String refreshToken);

    /** 撤销用户全部 refresh token（用户删除时调用） */
    void revokeAllByUser(Long userId);
}
