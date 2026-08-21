package com.yunsie.module.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户账号表（user_account）。密码不在本表 —— 凭证独立于 user_credential（登录凭证模型）。
 */
@Getter
@Setter
@TableName("user_account")
public class UserAccount extends BaseEntity {

    /** 登录账号 */
    private String username;

    /** 手机号(展示脱敏; 加密存储后续加固) */
    private String mobile;

    /** 昵称 */
    private String nickname;

    /** 头像URL */
    private String avatar;

    /** 用户类型: 1-学员 2-教师 */
    private Integer userType;

    /** 状态: 1-正常 0-禁用 2-锁定 */
    private Integer status;

    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;
}
