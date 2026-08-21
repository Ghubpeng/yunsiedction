package com.yunsie.module.course.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 小节-知识点关联入参（覆盖式；可选=空列表表示清空）。
 */
public record LessonNodeIdsReq(
        @NotEmpty(message = "节点列表不能为空") List<Long> nodeIds) {
}
