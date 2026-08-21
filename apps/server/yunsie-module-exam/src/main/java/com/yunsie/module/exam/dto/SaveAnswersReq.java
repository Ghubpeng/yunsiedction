package com.yunsie.module.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量暂存答案入参（upsert 幂等）。
 */
public record SaveAnswersReq(
        @NotEmpty(message = "答案列表不能为空") List<@Valid AnswerItem> answers) {

    public record AnswerItem(
            @NotNull(message = "试卷题目不能为空") Long paperQuestionId,
            @NotBlank(message = "答案不能为空") @Size(max = 50, message = "答案最长50字符") String answer) {
    }
}
