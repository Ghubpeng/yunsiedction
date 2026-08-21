package com.yunsie.module.subject.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 移动知识节点入参（newParentId=0 仅适用于章节级重排）。
 */
public record NodeMoveReq(
        @NotNull(message = "目标父节点不能为空") @Min(value = 0, message = "目标父节点非法") Long newParentId,
        Integer sort) {
}
