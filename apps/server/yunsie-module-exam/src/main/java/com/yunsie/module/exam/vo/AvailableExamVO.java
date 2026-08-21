package com.yunsie.module.exam.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 可参加考试 VO（用户端）。
 */
public record AvailableExamVO(
        Long id,
        String name,
        Integer durationMinutes,
        Integer questionCount,
        BigDecimal totalScore,
        BigDecimal passScore,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime validFrom,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime validUntil,
        Long inProgressAttemptId) {
}
