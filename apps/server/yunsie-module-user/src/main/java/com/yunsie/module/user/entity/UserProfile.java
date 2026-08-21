package com.yunsie.module.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 用户基础资料表（user_profile）。
 */
@Getter
@Setter
@TableName("user_profile")
public class UserProfile extends BaseEntity {

    /** 用户ID */
    private Long userId;

    /** 真实姓名 */
    private String realName;

    /** 性别: 0-未知 1-男 2-女 */
    private Integer gender;

    /** 生日 */
    private LocalDate birthday;

    /** 邮箱 */
    private String email;
}
