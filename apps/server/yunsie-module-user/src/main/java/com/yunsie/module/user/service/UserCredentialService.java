package com.yunsie.module.user.service;

/**
 * 登录凭证（user 域内部服务）。
 * 铁律：密码只以 BCrypt 哈希入库，绝不落明文（backend-development 安全条款）。
 */
public interface UserCredentialService {

    /** 设置/重置密码（哈希后入库，覆盖旧哈希） */
    void setPassword(Long userId, String rawPassword);

    /** 校验密码（供后续登录流程使用） */
    boolean verifyPassword(Long userId, String rawPassword);

    void removeByUser(Long userId);
}
