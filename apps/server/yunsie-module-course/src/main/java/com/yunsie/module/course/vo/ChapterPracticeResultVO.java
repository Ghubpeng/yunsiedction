package com.yunsie.module.course.vo;

/**
 * 章节练习上报结果（Stage 2.4）。
 */
public record ChapterPracticeResultVO(
        Long chapterId,
        Integer correctCount,
        Integer totalCount,
        Integer score,
        Integer finished) {
}
