package com.yunsie.module.learning.profile.algorithm;

import com.yunsie.module.learning.profile.algorithm.PredictionCalculator.PredictionResult;
import com.yunsie.module.learning.profile.config.MasteryParams.PredictionParams;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则版通过率预测单测：无历史=掌握度 / 历史加权 / 及格线回退 / 0~100 clamp / 低样本标注。
 */
class PredictionCalculatorTest {

    private final PredictionParams params =
            new PredictionParams("v1.0", 0.6, 3, 10, new BigDecimal("60"));

    @Test
    void noHistory_usesMastery() {
        PredictionResult r = PredictionCalculator.predict(List.of(), null, 75.0, 5, params);
        assertEquals(75, r.probability());
        assertTrue(r.basis().contains("无历史考试"));
        assertTrue(r.lowSample());
        assertEquals("v1.0", r.ruleVersion());
    }

    @Test
    void withHistory_weightedBlend() {
        // 最近 3 次：80, 60, 40 → 平均 60；及格线 100 → 得分比 60%；掌握度 80
        // 概率 = 0.6*60 + 0.4*80 = 68
        List<BigDecimal> scores = List.of(new BigDecimal("80"), new BigDecimal("60"), new BigDecimal("40"));
        PredictionResult r = PredictionCalculator.predict(scores, new BigDecimal("100"), 80.0, 100, params);
        assertEquals(68, r.probability());
        assertFalse(r.lowSample());
    }

    @Test
    void onlyRecentCountSamples() {
        // 6 次成绩，仅取最近 3 次（前 3 条）
        List<BigDecimal> scores = List.of(new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("0"), new BigDecimal("0"), new BigDecimal("0"));
        PredictionResult r = PredictionCalculator.predict(scores, new BigDecimal("100"), 0.0, 100, params);
        // 得分比 100% * 0.6 + 0*0.4 = 60
        assertEquals(60, r.probability());
    }

    @Test
    void nullPassScore_usesDefault() {
        List<BigDecimal> scores = List.of(new BigDecimal("60"));
        PredictionResult r = PredictionCalculator.predict(scores, null, 0.0, 100, params);
        // 60/60 = 100% → 0.6*100 + 0 = 60
        assertEquals(60, r.probability());
    }

    @Test
    void clamp_0to100() {
        PredictionResult high = PredictionCalculator.predict(
                List.of(new BigDecimal("1000")), new BigDecimal("10"), 100.0, 100, params);
        assertEquals(100, high.probability());
        PredictionResult low = PredictionCalculator.predict(List.of(), null, 0.0, 0, params);
        assertEquals(0, low.probability());
    }

    @Test
    void zeroPassScore_fallsBackToDefault() {
        List<BigDecimal> scores = List.of(new BigDecimal("60"));
        PredictionResult r = PredictionCalculator.predict(scores, BigDecimal.ZERO, 0.0, 100, params);
        assertEquals(60, r.probability());
    }
}
