package com.yunsie.module.user.vo;

import java.time.LocalDate;

/**
 * 用户基础资料 VO。
 */
public record ProfileVO(
        Long userId,
        String realName,
        Integer gender,
        LocalDate birthday,
        String email) {
}
