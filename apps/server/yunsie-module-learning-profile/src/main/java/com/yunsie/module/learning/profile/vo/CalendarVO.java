package com.yunsie.module.learning.profile.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * 学习日历视图（按月 + 连续学习天数）。
 */
public record CalendarVO(
        String month,
        Integer currentStreak,
        Integer longestStreak,
        List<DayItem> days) {

    public record DayItem(
            LocalDate studyDate,
            Long studySeconds,
            Integer practiceCount,
            Integer examCount) {
    }
}
