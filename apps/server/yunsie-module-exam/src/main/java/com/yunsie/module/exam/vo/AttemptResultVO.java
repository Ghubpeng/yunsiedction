package com.yunsie.module.exam.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 成绩详情 VO（已交卷后可见：含逐题对错/标准答案/解析/选项）。
 */
public record AttemptResultVO(
        Long attemptId,
        Long examId,
        String examName,
        BigDecimal score,
        Integer correctCount,
        Integer questionCount,
        BigDecimal passScore,
        Boolean passed,
        Integer durationMinutes,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime startedAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime submittedAt,
        List<ResultItemVO> items) {

    public record ResultItemVO(
            Long paperQuestionId,
            Integer sort,
            Integer questionType,
            String stem,
            String standardAnswer,
            String submittedAnswer,
            Boolean correct,
            BigDecimal score,
            String analysis,
            List<AttemptQuestionVO.AttemptOptionVO> options) {
    }
}
