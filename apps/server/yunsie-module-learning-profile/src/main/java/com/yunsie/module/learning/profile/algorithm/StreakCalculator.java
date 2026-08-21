package com.yunsie.module.learning.profile.algorithm;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * 连续学习天数算法（唯一实现，集中于此；learning-profile 连续学习）。
 * 规则：
 *   - 当前连续：以今天（或昨天，若今天尚未学习）为锚点向前连续计数；锚点不在集合中则为 0。
 *   - 历史最长连续：全部学习日的最长连续区间。
 */
public final class StreakCalculator {

    private StreakCalculator() {
    }

    /**
     * 当前连续学习天数（截至今天/昨天）。
     *
     * @param studyDates 学习日期集合（含练习/考试/看课任何学习日）
     * @param today      基准日（可注入，便于测试）
     */
    public static int currentStreak(Set<LocalDate> studyDates, LocalDate today) {
        if (studyDates == null || studyDates.isEmpty()) {
            return 0;
        }
        LocalDate anchor = studyDates.contains(today) ? today : today.minusDays(1);
        if (!studyDates.contains(anchor)) {
            return 0;
        }
        int streak = 0;
        LocalDate cursor = anchor;
        while (studyDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** 历史最长连续学习天数（任意区间） */
    public static int longestStreak(Set<LocalDate> studyDates) {
        if (studyDates == null || studyDates.isEmpty()) {
            return 0;
        }
        List<LocalDate> sorted = studyDates.stream().sorted().toList();
        int longest = 1;
        int current = 1;
        for (int i = 1; i < sorted.size(); i++) {
            if (ChronoUnit.DAYS.between(sorted.get(i - 1), sorted.get(i)) == 1) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 1;
            }
        }
        return longest;
    }
}
