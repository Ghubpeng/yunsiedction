package com.yunsie.module.learning.profile.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习历史汇总视图。
 */
public record SummaryVO(
        Long userId,
        Long totalStudySeconds,
        Integer courseFinishedLessons,
        Integer practiceCount,
        Integer practiceCorrectCount,
        Integer examCount,
        BigDecimal examBestScore,
        Integer streakDays,
        LocalDate lastStudyDate,
        LocalDateTime updatedAt) {
}
