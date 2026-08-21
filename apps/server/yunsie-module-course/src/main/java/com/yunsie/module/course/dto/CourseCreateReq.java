package com.yunsie.module.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建课程入参。
 */
public record CourseCreateReq(
        @NotNull(message = "所属证书不能为空") Long certificateId,
        @NotNull(message = "教师不能为空") Long teacherId,
        @NotBlank(message = "课程标题不能为空") @Size(max = 100, message = "课程标题最长100字符") String title,
        @Size(max = 1000, message = "课程简介最长1000字符") String description,
        Long subjectId,
        Long versionId,
        /** 归属学习章节（Stage 2.3A 内容树：证书→版本→科目→章节→课程；后台必选，接口保持可空语义） */
        Long chapterId) {
}
