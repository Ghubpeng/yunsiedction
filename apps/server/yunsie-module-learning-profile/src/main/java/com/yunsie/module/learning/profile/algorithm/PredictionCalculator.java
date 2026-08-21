package com.yunsie.module.learning.profile.algorithm;

import com.yunsie.module.learning.profile.config.MasteryParams.PredictionParams;

import java.math.BigDecimal;
import java.util.List;

/**
 * 规则版考试通过率预测（唯一实现，集中于此；learning-profile §7）。
 * 仅参考展示、版本化、不参与任何自动决策，禁止"保证通过"类语义。
 *
 * 规则 v1（可解释）：
 *   - 无历史考试：概率 = 知识点平均掌握度。
 *   - 有历史考试：概率 = historyWeight * 历史得分比（最近N次平均分/及格线*100）
 *                     + (1-historyWeight) * 平均掌握度。
 *   - 练习样本低于 minSamples 时标注参考性弱。
 */
public final class PredictionCalculator {

    private PredictionCalculator() {
    }

    /**
     * @param scoresNewestFirst 历史考试得分（新→旧）
     * @param passScore         及格线（null 或 <=0 时用配置默认值）
     * @param avgMastery        知识点平均掌握度 0~100
     * @param practiceCount     练习总量
     */
    public static PredictionResult predict(List<BigDecimal> scoresNewestFirst, BigDecimal passScore,
                                           double avgMastery, int practiceCount, PredictionParams params) {
        BigDecimal passLine = resolvePassLine(passScore, params);
        double scoreRatio;
        String basis;
        if (scoresNewestFirst == null || scoresNewestFirst.isEmpty()) {
            scoreRatio = clamp01(avgMastery);
            basis = "无历史考试成绩，概率基于知识点平均掌握度估算";
        } else {
            int take = Math.min(params.recentCount(), scoresNewestFirst.size());
            List<BigDecimal> recent = scoresNewestFirst.subList(0, take);
            double avg = recent.stream().map(BigDecimal::doubleValue)
                    .mapToDouble(Double::doubleValue).average().orElse(0.0);
            scoreRatio = clamp01(avg / passLine.doubleValue() * 100.0);
            basis = "基于最近" + take + "次考试平均得分与知识点平均掌握度加权"
                    + "(历史权重 " + Math.round(params.historyWeight() * 100) + "%)";
        }
        double probability = scoresNewestFirst == null || scoresNewestFirst.isEmpty()
                ? scoreRatio
                : params.historyWeight() * scoreRatio + (1 - params.historyWeight()) * clamp01(avgMastery);
        boolean lowSample = practiceCount < params.minSamples();
        if (lowSample) {
            basis += "；练习样本较少，结果仅供参考";
        }
        return new PredictionResult((int) Math.round(clamp01(probability)), params.ruleVersion(), basis, lowSample);
    }

    private static BigDecimal resolvePassLine(BigDecimal passScore, PredictionParams params) {
        if (passScore != null && passScore.compareTo(BigDecimal.ZERO) > 0) {
            return passScore;
        }
        return params.defaultPassScore();
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    /** 预测结果（不可变；probability 0~100 整数） */
    public record PredictionResult(int probability, String ruleVersion, String basis, boolean lowSample) {
    }
}
