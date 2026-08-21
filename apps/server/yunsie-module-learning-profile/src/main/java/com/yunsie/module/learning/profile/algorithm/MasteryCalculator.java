package com.yunsie.module.learning.profile.algorithm;

import com.yunsie.module.learning.profile.config.MasteryParams;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 掌握度算法（唯一实现，集中于此；learning-profile §5 强制：禁止散落/硬编码）。
 * 规则算法（简单、稳定、可解释），不引入 ML：
 *   - 答对加分：递减巩固 correctWeight / (1 + consolidationFactor * k)（多次正确逐步巩固）
 *   - 答错减分：递增惩罚 wrongWeight * (1 + penaltyFactor * j)（多次错误明显下降）
 *   - 时间衰减：超过宽限期后按 (1 - decayRate)^天数 指数衰减（艾宾浩斯近似）
 *   - 最终 clamp 到 [minMastery, maxMastery]（0~100）
 * 参数全部来自 learn_config（MasteryParams）。
 */
public final class MasteryCalculator {

    private MasteryCalculator() {
    }

    /**
     * 由节点累计作答次数聚合计算掌握度（0~100）。
     *
     * @param correctCount    累计答对次数
     * @param wrongCount      累计答错次数
     * @param lastPracticeAt  最近练习时间（null=从未练习或未知，不衰减）
     * @param today           计算基准日（可注入，便于测试）
     * @param params          配置参数
     */
    public static int aggregate(int correctCount, int wrongCount, LocalDateTime lastPracticeAt,
                                LocalDate today, MasteryParams params) {
        double value = 0.0;
        int safeCorrect = Math.max(0, correctCount);
        int safeWrong = Math.max(0, wrongCount);
        for (int k = 0; k < safeCorrect; k++) {
            value += params.correctWeight() / (1 + params.consolidationFactor() * k);
        }
        for (int j = 0; j < safeWrong; j++) {
            value -= params.wrongWeight() * (1 + params.penaltyFactor() * j);
        }
        if (lastPracticeAt != null) {
            long days = ChronoUnit.DAYS.between(lastPracticeAt.toLocalDate(), today);
            if (days > params.decayGraceDays()) {
                long decayDays = days - params.decayGraceDays();
                value *= Math.pow(1 - params.decayRate(), decayDays);
            }
        }
        return clamp(value, params);
    }

    /** 夹取到 [min, max]，并对 int 向下取整边界保护 */
    public static int clamp(double value, MasteryParams params) {
        if (Double.isNaN(value)) {
            return params.minMastery();
        }
        int rounded = (int) Math.round(value);
        return Math.max(params.minMastery(), Math.min(params.maxMastery(), rounded));
    }
}
