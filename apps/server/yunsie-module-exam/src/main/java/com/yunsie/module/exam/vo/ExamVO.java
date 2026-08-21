package com.yunsie.module.exam.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试 VO（管理端）。
 */
public record ExamVO(
        Long id,
        Long certificateId,
        Long subjectId,
        Long versionId,
        String name,
        Integer durationMinutes,
        BigDecimal totalScore,
        BigDecimal passScore,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime validFrom,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime validUntil,
        Integer status,
        AssembleRuleVO rule) {

    public record AssembleRuleVO(Integer questionCount, List<Integer> questionTypes,
                                 List<Long> nodeIds, Integer difficulty) {
    }
}
