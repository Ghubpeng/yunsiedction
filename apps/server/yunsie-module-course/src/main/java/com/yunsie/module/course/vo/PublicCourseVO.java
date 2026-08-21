package com.yunsie.module.course.vo;

/**
 * 公开课程 VO（用户端列表）。
 */
public record PublicCourseVO(
        Long id,
        String title,
        String description,
        Integer chapterCount,
        Integer lessonCount) {
}
