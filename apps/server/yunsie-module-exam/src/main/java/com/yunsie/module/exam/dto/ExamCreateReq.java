package com.yunsie.module.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建考试入参（含组卷规则，配置化 JSON）。
 */
public record ExamCreateReq(
        @NotNull(message = "所属证书不能为空") Long certificateId,
        Long subjectId,
        Long versionId,
        @NotBlank(message = "考试名称不能为空") @Size(max = 100, message = "考试名称最长100字符") String name,
        @NotNull(message = "考试时长不能为空")
        @Min(value = 1, message = "时长最少1分钟") @Max(value = 300, message = "时长最长300分钟")
        Integer durationMinutes,
        @DecimalMin(value = "0", message = "及格线不能为负") BigDecimal passScore,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        @NotNull(message = "组卷规则不能为空") @Valid AssembleRule rule) {

    /**
     * 组卷规则（配置化，禁止硬编码）。
     */
    public record AssembleRule(
            @NotNull(message = "题目总数不能为空")
            @Min(value = 1, message = "题目总数最少1题") @Max(value = 200, message = "题目总数最多200题")
            Integer questionCount,
            @NotEmpty(message = "题型集合不能为空") List<@Min(value = 1, message = "题型非法") @Max(value = 3, message = "题型非法") Integer> questionTypes,
            List<Long> nodeIds,
            @Min(value = 1, message = "难度非法") @Max(value = 3, message = "难度非法") Integer difficulty) {
    }
}
