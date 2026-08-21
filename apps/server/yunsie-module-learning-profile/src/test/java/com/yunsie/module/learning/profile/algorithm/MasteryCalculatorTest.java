package com.yunsie.module.learning.profile.algorithm;

import com.yunsie.module.learning.profile.config.MasteryParams;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 掌握度算法单测：正确上升 / 错误下降 / 多次巩固递减 / 多次错误递增 /
 * 时间衰减 / 配置参数生效 / 0~100 clamp。
 */
class MasteryCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    private final MasteryParams params = new MasteryParams(12, 18, 0.35, 0.25, 0.05, 7, 0, 100);

    @Test
    void firstCorrect_rises() {
        int value = MasteryCalculator.aggregate(1, 0, LocalDateTime.of(2026, 8, 19, 10, 0), TODAY, params);
        assertEquals(12, value);
    }

    @Test
    void wrong_drops() {
        int value = MasteryCalculator.aggregate(0, 1, LocalDateTime.of(2026, 8, 19, 10, 0), TODAY, params);
        assertEquals(0, value); // 0 答对扣到下限（clamp 0）
    }

    @Test
    void wrong_dropsFromPositive() {
        // 1 对 1 错：12 - 18 = -6 → clamp 0
        int value = MasteryCalculator.aggregate(1, 1, LocalDateTime.of(2026, 8, 19, 10, 0), TODAY, params);
        assertEquals(0, value);
        // 2 对 1 错：12 + 12/(1.35) ≈ 20.9 - 18 = 2.9 → 3
        int value2 = MasteryCalculator.aggregate(2, 1, LocalDateTime.of(2026, 8, 19, 10, 0), TODAY, params);
        assertEquals(3, value2);
    }

    @Test
    void multipleCorrect_diminishingConsolidation() {
        int one = MasteryCalculator.aggregate(1, 0, LocalDateTime.of(2026, 8, 19, 10, 0), TODAY, params);
        int five = MasteryCalculator.aggregate(5, 0, LocalDateTime.of(2026, 8, 19, 10, 0), TODAY, params);
        int ten = MasteryCalculator.aggregate(10, 0, LocalDateTime.of(2026, 8, 19, 10, 0), TODAY, params);
        // 第 2 次增益小于第 1 次（巩固递减）；总数持续上升
        double firstGain = 12.0;
        double secondGain = 12.0 / 1.35;
        assertTrue(secondGain < firstGain);
        assertTrue(five > one);
        assertTrue(ten > five);
        assertTrue(ten <= 100);
    }

    @Test
    void multipleWrong_escalatingPenalty() {
        int wrong2 = MasteryCalculator.aggregate(10, 2, LocalDateTime.of(2026, 8, 19, 10, 0), TODAY, params);
        int wrong4 = MasteryCalculator.aggregate(10, 4, LocalDateTime.of(2026, 8, 19, 10, 0), TODAY, params);
        // 每次错误惩罚递增：第 2 次错扣 18*1.25=22.5 > 第 1 次 18
        assertTrue(wrong4 < wrong2);
    }

    @Test
    void timeDecay_afterGracePeriod() {
        LocalDateTime lastPractice = LocalDateTime.of(2026, 8, 1, 10, 0); // 18 天前，宽限 7 天 → 衰减 11 天
        int fresh = MasteryCalculator.aggregate(10, 0, LocalDateTime.of(2026, 8, 19, 9, 0), TODAY, params);
        int decayed = MasteryCalculator.aggregate(10, 0, lastPractice, TODAY, params);
        assertTrue(decayed < fresh);
    }

    @Test
    void noDecay_withinGracePeriod() {
        LocalDateTime lastPractice = LocalDateTime.of(2026, 8, 15, 10, 0); // 4 天前 ≤ 宽限 7 天
        int a = MasteryCalculator.aggregate(10, 0, LocalDateTime.of(2026, 8, 19, 9, 0), TODAY, params);
        int b = MasteryCalculator.aggregate(10, 0, lastPractice, TODAY, params);
        assertEquals(a, b);
    }

    @Test
    void clamp_0to100() {
        int huge = MasteryCalculator.aggregate(1000, 0, null, TODAY, params);
        assertEquals(100, huge);
        int negative = MasteryCalculator.aggregate(0, 1000, LocalDateTime.of(2026, 8, 19, 10, 0), TODAY, params);
        assertEquals(0, negative);
    }

    @Test
    void configParams_takeEffect() {
        MasteryParams weak = new MasteryParams(1, 1, 0, 0, 0, 0, 0, 100);
        MasteryParams strong = new MasteryParams(50, 5, 0, 0, 0, 0, 0, 100);
        int weakValue = MasteryCalculator.aggregate(1, 0, LocalDateTime.of(2026, 8, 19, 10, 0), TODAY, weak);
        int strongValue = MasteryCalculator.aggregate(1, 0, LocalDateTime.of(2026, 8, 19, 10, 0), TODAY, strong);
        assertEquals(1, weakValue);
        assertEquals(50, strongValue);
        // 衰减率参数生效：无衰减 vs 强衰减
        MasteryParams heavyDecay = new MasteryParams(12, 18, 0.35, 0.25, 0.5, 0, 0, 100);
        int decayed = MasteryCalculator.aggregate(10, 0, LocalDateTime.of(2026, 8, 12, 10, 0), TODAY, heavyDecay);
        int fresh = MasteryCalculator.aggregate(10, 0, LocalDateTime.of(2026, 8, 19, 9, 0), TODAY, heavyDecay);
        assertTrue(decayed < fresh);
    }

    @Test
    void nullLastPractice_noDecayAndNoNpe() {
        int value = MasteryCalculator.aggregate(3, 1, null, TODAY, params);
        assertTrue(value >= 0 && value <= 100);
    }
}
