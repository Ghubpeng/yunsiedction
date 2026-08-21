package com.yunsie.module.certificate.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 移动分类入参（newParentId=0 表示移动到根）。
 */
public record CategoryMoveReq(
        @NotNull(message = "目标父分类ID不能为空") @Min(value = 0, message = "目标父分类ID非法") Long newParentId,
        Integer sort) {
}
