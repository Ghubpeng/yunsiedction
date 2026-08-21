package com.yunsie.module.question.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 错题 VO（用户端）。
 */
public record MistakeVO(
        Long id,
        Long questionId,
        Integer questionType,
        String stem,
        Integer mistakeCount,
        Integer status,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime lastMistakeTime,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime lastPracticeTime) {
}
