package com.yunsie.module.course.vo;

import java.util.List;

/**
 * 用户端课程树 VO（章节+小节+本人进度；含内容树归属——证书/科目在学习过程中展示）。
 */
public record CourseTreeVO(
        Long courseId,
        String title,
        String description,
        /** 内容树归属（Stage 2.1）：证书→版本→科目→章节→课程 */
        Long subjectId,
        Long versionId,
        Long chapterId,
        /** 内容树归属名称（Stage 2.2：用户端展示 证书→科目→章节） */
        String certificateName,
        String subjectName,
        String chapterName,
        List<ChapterVO> chapters) {
}
