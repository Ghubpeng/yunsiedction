package com.yunsie.module.exam.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 开始考试响应 VO（幂等：进行中返回既有 attempt）。
 */
public record AttemptStartVO(
        Long attemptId,
        Long examId,
        Integer status,
        String examName,
        Integer durationMinutes,
        Integer questionCount,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime startedAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime expiredAt,
        List<AttemptQuestionVO> questions) {
}
