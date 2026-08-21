package com.yunsie.module.exam.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试卷摘要 VO（管理端）。
 */
public record PaperSummaryVO(
        Long paperId,
        String title,
        Integer durationMinutes,
        BigDecimal totalScore,
        Integer questionCount,
        Integer status,
        Long assembleSeed,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime createTime) {
}
