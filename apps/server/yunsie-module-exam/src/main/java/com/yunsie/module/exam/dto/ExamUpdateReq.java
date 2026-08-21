package com.yunsie.module.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 更新考试入参（仅草稿/已下架可编辑）。
 */
public record ExamUpdateReq(
        @NotBlank(message = "考试名称不能为空") @Size(max = 100, message = "考试名称最长100字符") String name,
        @NotNull(message = "考试时长不能为空")
        @Min(value = 1, message = "时长最少1分钟") @Max(value = 300, message = "时长最长300分钟")
        Integer durationMinutes,
        @DecimalMin(value = "0", message = "及格线不能为负") BigDecimal passScore,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        @NotNull(message = "组卷规则不能为空") @Valid ExamCreateReq.AssembleRule rule) {
}
