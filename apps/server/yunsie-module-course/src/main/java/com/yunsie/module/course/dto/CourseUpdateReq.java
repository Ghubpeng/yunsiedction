package com.yunsie.module.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新课程入参（仅草稿/已下架可编辑）。
 */
public record CourseUpdateReq(
        @NotBlank(message = "课程标题不能为空") @Size(max = 100, message = "课程标题最长100字符") String title,
        @Size(max = 1000, message = "课程简介最长1000字符") String description,
        Long subjectId,
        Long versionId,
        Long chapterId) {
}
