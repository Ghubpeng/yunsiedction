package com.yunsie.module.learning.profile.config;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 掌握度算法参数（全部来自 learn_config，禁止硬编码魔法数字；learning-profile §5）。
 * 静态默认值与 V8 种子一致，配置缺失/非法时兜底。
 */
public record MasteryParams(
        double correctWeight,
        double wrongWeight,
        double consolidationFactor,
        double penaltyFactor,
        double decayRate,
        int decayGraceDays,
        int minMastery,
        int maxMastery) {

    public static final MasteryParams DEFAULTS = new MasteryParams(
            12.0, 18.0, 0.35, 0.25, 0.05, 7, 0, 100);

    /** 从 learn_config 键值对解析；缺失/非法键回退默认值 */
    public static MasteryParams from(Map<String, String> config) {
        if (config == null || config.isEmpty()) {
            return DEFAULTS;
        }
        return new MasteryParams(
                parseDouble(config, "learn.mastery.correct.weight", DEFAULTS.correctWeight),
                parseDouble(config, "learn.mastery.wrong.weight", DEFAULTS.wrongWeight),
                parseDouble(config, "learn.mastery.consolidation.factor", DEFAULTS.consolidationFactor),
                parseDouble(config, "learn.mastery.penalty.factor", DEFAULTS.penaltyFactor),
                parseDouble(config, "learn.mastery.decay.rate", DEFAULTS.decayRate),
                parseInt(config, "learn.mastery.decay.grace.days", DEFAULTS.decayGraceDays),
                parseInt(config, "learn.mastery.min", DEFAULTS.minMastery),
                parseInt(config, "learn.mastery.max", DEFAULTS.maxMastery));
    }

    private static double parseDouble(Map<String, String> config, String key, double fallback) {
        try {
            return Double.parseDouble(config.getOrDefault(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(Map<String, String> config, String key, int fallback) {
        try {
            return Integer.parseInt(config.getOrDefault(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 考试通过率预测参数（learn_config；规则版本可回溯，learning-profile §7） */
    public record PredictionParams(
            String ruleVersion,
            double historyWeight,
            int recentCount,
            int minSamples,
            BigDecimal defaultPassScore) {

        public static final PredictionParams DEFAULTS = new PredictionParams(
                "v1.0", 0.6, 3, 10, new BigDecimal("60"));

        public static PredictionParams from(Map<String, String> config) {
            if (config == null || config.isEmpty()) {
                return DEFAULTS;
            }
            double weight = parseDouble(config, "learn.prediction.history.weight", DEFAULTS.historyWeight);
            return new PredictionParams(
                    config.getOrDefault("learn.prediction.rule.version", DEFAULTS.ruleVersion),
                    Math.max(0.0, Math.min(1.0, weight)),
                    parseInt(config, "learn.prediction.recent.count", DEFAULTS.recentCount),
                    parseInt(config, "learn.prediction.min.samples", DEFAULTS.minSamples),
                    new BigDecimal(config.getOrDefault("learn.prediction.default.pass.score",
                            DEFAULTS.defaultPassScore.toPlainString())));
        }

        private static double parseDouble(Map<String, String> config, String key, double fallback) {
            try {
                return Double.parseDouble(config.getOrDefault(key, String.valueOf(fallback)));
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        private static int parseInt(Map<String, String> config, String key, int fallback) {
            try {
                return Integer.parseInt(config.getOrDefault(key, String.valueOf(fallback)));
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
    }
}
