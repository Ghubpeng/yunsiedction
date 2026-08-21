package com.yunsie.module.question.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 练习提交入参（answer 为原始答案，服务端标准化后判分）。
 */
public record PracticeSubmitReq(
        @NotNull(message = "题目不能为空") Long questionId,
        @NotBlank(message = "答案不能为空") @Size(max = 50, message = "答案最长50字符") String answer,
        @NotNull(message = "练习模式不能为空")
        @Min(value = 1, message = "练习模式非法") @Max(value = 4, message = "练习模式非法")
        Integer mode,
        Long nodeId,
        @Min(value = 0, message = "响应耗时非法") Integer responseTimeMs) {
}
