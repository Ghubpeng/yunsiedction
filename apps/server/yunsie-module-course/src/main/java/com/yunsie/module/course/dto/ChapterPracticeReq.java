package com.yunsie.module.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 章节练习完成上报（Stage 2.4；判分由服务端完成，前端仅汇总服务端返回的对错数）。
 */
public record ChapterPracticeReq(
        @NotNull(message = "答对题数不能为空") @Min(value = 0, message = "答对题数非法") Integer correctCount,
        @NotNull(message = "总题数不能为空") @Min(value = 1, message = "总题数非法") @Max(value = 500, message = "题数非法") Integer totalCount) {
}
