package com.yunsie.module.exam.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交卷结果 VO。
 */
public record SubmitResultVO(
        Long attemptId,
        Integer status,
        BigDecimal score,
        Integer correctCount,
        Integer questionCount,
        BigDecimal passScore,
        Boolean passed,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime submittedAt) {
}
