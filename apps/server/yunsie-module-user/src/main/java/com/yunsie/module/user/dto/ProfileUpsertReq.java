package com.yunsie.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 用户基础资料 upsert 入参。
 */
public record ProfileUpsertReq(
        @Size(max = 50, message = "真实姓名最长50字符") String realName,
        @Min(value = 0, message = "性别非法") @Max(value = 2, message = "性别非法") Integer gender,
        @Past(message = "生日必须早于今天") LocalDate birthday,
        @Email(message = "邮箱格式非法") @Size(max = 100, message = "邮箱最长100字符") String email) {
}
