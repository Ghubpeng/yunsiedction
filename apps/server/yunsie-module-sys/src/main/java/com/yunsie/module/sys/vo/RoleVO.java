package com.yunsie.module.sys.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 角色 VO。
 */
public record RoleVO(
        Long id,
        String roleCode,
        String roleName,
        Integer roleType,
        Integer status,
        String remark,
        Integer sort,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime createTime) {
}
