package com.yunsie.module.course.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 进度上报入参。
 */
public record ProgressReq(
        @NotNull(message = "小节不能为空") Long lessonId,
        @NotNull(message = "进度秒数不能为空") @Min(value = 0, message = "进度秒数非法") Integer positionSeconds) {
}
