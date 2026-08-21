package com.yunsie.module.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新章节入参（已发布课程仅允许启用/禁用，标题/排序编辑被拒）。
 */
public record ChapterUpdateReq(
        @NotBlank(message = "章节标题不能为空") @Size(max = 100, message = "章节标题最长100字符") String title,
        Integer sort) {
}
