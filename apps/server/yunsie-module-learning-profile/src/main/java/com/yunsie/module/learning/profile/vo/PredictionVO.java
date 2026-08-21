package com.yunsie.module.learning.profile.vo;

import java.time.LocalDateTime;

/**
 * 考试通过率预测视图（规则版，仅参考；不参与任何自动决策）。
 */
public record PredictionVO(
        Long examId,
        String examName,
        Integer probability,
        String ruleVersion,
        String basis,
        Boolean lowSample,
        LocalDateTime calculatedAt) {
}
