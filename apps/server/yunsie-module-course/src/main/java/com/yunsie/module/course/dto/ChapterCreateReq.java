package com.yunsie.module.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建章节入参。
 */
public record ChapterCreateReq(
        @NotNull(message = "所属课程不能为空") Long courseId,
        @NotBlank(message = "章节标题不能为空") @Size(max = 100, message = "章节标题最长100字符") String title,
        Integer sort) {
}
