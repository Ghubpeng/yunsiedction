package com.yunsie.module.exam.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试交卷完成事件（扩展点）。
 * 本阶段仅发布事件、不实现消费者（learning-profile 后续阶段接入，见 docs/CONFLICTS.md #15）。
 */
public record ExamFinishedEvent(
        Long attemptId,
        Long userId,
        Long examId,
        BigDecimal score,
        Integer correctCount,
        Integer questionCount,
        LocalDateTime submittedAt) {
}
