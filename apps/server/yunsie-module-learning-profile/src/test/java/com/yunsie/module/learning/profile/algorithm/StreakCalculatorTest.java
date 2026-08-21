package com.yunsie.module.learning.profile.algorithm;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 连续学习天数算法单测：单日 / 连续 2 天 / 跨月 / 跨年 / 断档 / 今天未学习 / 历史最长连续。
 */
class StreakCalculatorTest {

    @Test
    void singleDay() {
        Set<LocalDate> dates = Set.of(LocalDate.of(2026, 8, 19));
        assertEquals(1, StreakCalculator.currentStreak(dates, LocalDate.of(2026, 8, 19)));
    }

    @Test
    void twoConsecutiveDays() {
        Set<LocalDate> dates = Set.of(LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 19));
        assertEquals(2, StreakCalculator.currentStreak(dates, LocalDate.of(2026, 8, 19)));
    }

    @Test
    void crossMonth() {
        Set<LocalDate> dates = Set.of(LocalDate.of(2026, 7, 30), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 1));
        assertEquals(3, StreakCalculator.currentStreak(dates, LocalDate.of(2026, 8, 1)));
    }

    @Test
    void crossYear() {
        Set<LocalDate> dates = Set.of(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1));
        assertEquals(2, StreakCalculator.currentStreak(dates, LocalDate.of(2026, 1, 1)));
    }

    @Test
    void gapBreaksStreak() {
        Set<LocalDate> dates = Set.of(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 19));
        // 昨天(18)未学习且今天(19)已学习 → 当前连续=1（仅今天）
        assertEquals(1, StreakCalculator.currentStreak(dates, LocalDate.of(2026, 8, 19)));
    }

    @Test
    void todayNotStudied_countsFromYesterday() {
        Set<LocalDate> dates = Set.of(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 18));
        assertEquals(2, StreakCalculator.currentStreak(dates, LocalDate.of(2026, 8, 19)));
    }

    @Test
    void neitherTodayNorYesterday_zero() {
        Set<LocalDate> dates = Set.of(LocalDate.of(2026, 8, 17));
        assertEquals(0, StreakCalculator.currentStreak(dates, LocalDate.of(2026, 8, 19)));
    }

    @Test
    void empty_zero() {
        assertEquals(0, StreakCalculator.currentStreak(Set.of(), LocalDate.of(2026, 8, 19)));
        assertEquals(0, StreakCalculator.longestStreak(Set.of()));
    }

    @Test
    void longestStreak_historicalInterval() {
        Set<LocalDate> dates = Set.of(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 12),
                LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 14));
        assertEquals(5, StreakCalculator.longestStreak(dates));
    }
}
