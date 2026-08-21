package com.yunsie.module.question.vo;

import java.util.List;

/**
 * 题目详情 VO（管理端，含解析/答案/选项/知识点）。
 */
public record QuestionDetailVO(
        QuestionVO question,
        String analysis,
        String answer,
        List<OptionVO> options,
        List<Long> nodeIds) {
}
