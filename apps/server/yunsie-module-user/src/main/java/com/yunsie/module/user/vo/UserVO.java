package com.yunsie.module.user.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户 VO（roleIds 经 sys 域契约 SysUserRoleApi 查询，不直连 sys 表）。
 */
public record UserVO(
        Long id,
        String username,
        String mobile,
        String nickname,
        String avatar,
        Integer userType,
        Integer status,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime lastLoginTime,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime createTime,
        List<Long> roleIds) {
}
