package com.yunsie.module.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 驳回题目入参。
 */
public record QuestionRejectReq(
        @NotBlank(message = "驳回理由不能为空") @Size(max = 255, message = "驳回理由最长255字符") String reason) {
}
