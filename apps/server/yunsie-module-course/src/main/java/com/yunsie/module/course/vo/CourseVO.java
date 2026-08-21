package com.yunsie.module.course.vo;

/**
 * 课程 VO（管理端）。
 */
public record CourseVO(
        Long id,
        Long certificateId,
        Long subjectId,
        Long versionId,
        Long chapterId,
        String chapterName,
        Long teacherId,
        String title,
        String description,
        Integer type,
        Integer status,
        Integer chapterCount,
        Integer lessonCount) {
}
