package com.yunsie.module.exam.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户端考试题目 VO（不含答案与解析）。
 */
public record AttemptQuestionVO(
        Long paperQuestionId,
        Integer sort,
        Integer questionType,
        String stem,
        List<AttemptOptionVO> options,
        String savedAnswer) {

    public record AttemptOptionVO(String optionKey, String content) {
    }
}
