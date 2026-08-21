package com.yunsie.module.course.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 播放凭证 VO（短有效期预签名 URL）。
 */
public record PlayVO(
        String url,
        long expiresInSeconds,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime expiresAt) {
}
