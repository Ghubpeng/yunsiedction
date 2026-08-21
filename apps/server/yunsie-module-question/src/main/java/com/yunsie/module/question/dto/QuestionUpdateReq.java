package com.yunsie.module.question.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 更新题目入参（整体提交选项；知识点关联经 /knowledge-nodes 独立调整）。
 * 编辑规则（服务层）：草稿/驳回/已下架直接改；已发布 → content_version+1 并回草稿重审。
 */
public record QuestionUpdateReq(
        @NotBlank(message = "题干不能为空") @Size(max = 2000, message = "题干最长2000字符") String stem,
        @NotBlank(message = "解析不能为空") @Size(max = 2000, message = "解析最长2000字符") String analysis,
        @NotBlank(message = "答案不能为空") @Size(max = 50, message = "答案最长50字符") String answer,
        @Min(value = 1, message = "难度非法") @Max(value = 3, message = "难度非法") Integer difficulty,
        @Min(value = 1, message = "来源非法") @Max(value = 3, message = "来源非法") Integer source,
        List<QuestionCreateReq.OptionItem> options) {
}
