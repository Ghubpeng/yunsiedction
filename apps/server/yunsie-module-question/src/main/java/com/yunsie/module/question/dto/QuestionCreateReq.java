package com.yunsie.module.question.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建题目入参（含选项与知识点关联，整体提交）。
 * 判断题 optionItems 必须为空；选择类必须有选项。
 */
public record QuestionCreateReq(
        @NotNull(message = "所属证书不能为空") Long certificateId,
        @NotNull(message = "题型不能为空")
        @Min(value = 1, message = "题型非法") @Max(value = 3, message = "题型非法")
        Integer questionType,
        @NotBlank(message = "题干不能为空") @Size(max = 2000, message = "题干最长2000字符") String stem,
        @NotBlank(message = "解析不能为空") @Size(max = 2000, message = "解析最长2000字符") String analysis,
        @NotBlank(message = "答案不能为空") @Size(max = 50, message = "答案最长50字符") String answer,
        @Min(value = 1, message = "难度非法") @Max(value = 3, message = "难度非法") Integer difficulty,
        @Min(value = 1, message = "来源非法") @Max(value = 3, message = "来源非法") Integer source,
        List<@Valid OptionItem> options,
        @NotEmpty(message = "必须关联至少一个知识点/子知识点") List<Long> nodeIds) {

    public record OptionItem(
            @NotBlank(message = "选项键不能为空") String optionKey,
            @NotBlank(message = "选项内容不能为空") @Size(max = 500, message = "选项内容最长500字符") String content) {
    }
}
