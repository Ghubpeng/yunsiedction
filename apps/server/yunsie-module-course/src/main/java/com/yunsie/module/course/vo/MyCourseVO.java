package com.yunsie.module.course.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 我的课程 VO（续播入口）。
 */
public record MyCourseVO(
        Long courseId,
        String title,
        String description,
        Long lastLessonId,
        String lastLessonTitle,
        Integer lastPositionSeconds,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime lastLearnTime) {
}
