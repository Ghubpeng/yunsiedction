package com.yunsie.module.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 刷新令牌表（user_refresh_token）。
 * 铁律：仅存 SHA-256 哈希，不存明文 token；支持轮换（refresh 时撤销旧令牌）与撤销（logout/禁用）。
 * 不使用 Redis 会话/黑名单（单实例起步，DB 哈希即可满足；access token 短 TTL 收敛泄露风险）。
 */
@Getter
@Setter
@TableName("user_refresh_token")
public class UserRefreshToken extends BaseEntity {

    /** 用户ID */
    private Long userId;

    /** 刷新令牌 SHA-256 哈希（64位hex） */
    private String tokenHash;

    /** 过期时间 */
    private LocalDateTime expiresAt;

    /** 是否已撤销: 0-否 1-是 */
    private Integer revoked;

    /** 备注 */
    private String remark;
}
