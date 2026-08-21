package com.yunsie.module.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 启用/禁用入参（章节/小节通用；已发布课程允许此操作）。
 */
public record StatusReq(
        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态非法") @Max(value = 1, message = "状态非法")
        Integer status) {
}
