package com.yunsie.module.question.vo;

/**
 * 练习判分结果 VO（提交后即时反馈）。
 */
public record PracticeSubmitResultVO(
        Long questionId,
        Boolean correct,
        String standardAnswer,
        String analysis) {
}
