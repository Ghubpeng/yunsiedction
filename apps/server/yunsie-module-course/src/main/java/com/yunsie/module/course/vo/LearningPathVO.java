package com.yunsie.module.course.vo;

import java.util.List;

/**
 * 学习路径视图（Stage 2.4）：以考试目标驱动的课程学习路径。
 * 课程按 证书→科目→章节→课时 组织；章节四态由服务端计算（前端不复制业务规则）。
 */
public record LearningPathVO(
        Long certificateId,
        String certificateName,
        Integer totalChapters,
        Integer finishedChapters,
        Integer masteredChapters,
        Integer percent,
        String stage,
        NextTaskVO nextTask,
        List<SubjectBlockVO> subjects) {

    public record NextTaskVO(Long courseId, String courseTitle, Long chapterId, String chapterTitle,
                             Long lessonId, String lessonTitle, String type) {
    }

    public record SubjectBlockVO(Long subjectId, String subjectName, List<CourseBlockVO> courses) {
    }

    public record CourseBlockVO(Long courseId, String title, List<ChapterBlockVO> chapters) {
    }

    public record ChapterBlockVO(Long chapterId, String title, String state, Integer practiceScore) {
    }
}
