package com.yunsie.module.course.vo;

import java.util.List;

/**
 * 章节 VO（管理端/用户端树共用）。
 */
public record ChapterVO(
        Long id,
        String title,
        Integer sort,
        Integer status,
        List<LessonVO> lessons,
        Integer finished,
        /** Stage 2.4：章节练习是否完成（1-是；匿名/未练习为 0） */
        Integer practiceFinished,
        /** Stage 2.4：最近一次章节练习得分（0~100；未练习为 null） */
        Integer practiceScore) {
}
