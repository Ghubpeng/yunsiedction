package com.yunsie.module.course.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新小节入参（已发布课程仅允许启用/禁用）。
 */
public record LessonUpdateReq(
        @NotBlank(message = "小节标题不能为空") @Size(max = 100, message = "小节标题最长100字符") String title,
        @Min(value = 0, message = "时长非法") Integer durationSeconds,
        Integer sort) {
}
