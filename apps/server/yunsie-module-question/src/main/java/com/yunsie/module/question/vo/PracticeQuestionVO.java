package com.yunsie.module.question.vo;

import java.util.List;

/**
 * 练习题目 VO（用户端，不含答案与解析）。
 */
public record PracticeQuestionVO(
        Long id,
        Integer questionType,
        String stem,
        List<OptionVO> options) {
}
