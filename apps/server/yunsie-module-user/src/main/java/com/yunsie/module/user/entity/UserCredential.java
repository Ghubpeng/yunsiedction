package com.yunsie.module.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户登录凭证表（user_credential）。
 * 铁律：secret 只存 BCrypt 哈希，严禁明文；短信验证码等凭证类型后续经 credential_type 扩展。
 */
@Getter
@Setter
@TableName("user_credential")
public class UserCredential extends BaseEntity {

    /** 用户ID */
    private Long userId;

    /** 凭证类型: 1-密码(预留扩展: 短信验证码等) */
    private Integer credentialType;

    /** 凭证密文(密码=BCrypt哈希; 严禁明文) */
    private String secret;

    /** 状态: 1-正常 0-禁用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
