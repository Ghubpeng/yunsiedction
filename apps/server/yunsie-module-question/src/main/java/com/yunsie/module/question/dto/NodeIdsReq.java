package com.yunsie.module.question.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 题目-知识点关联入参（覆盖式）。
 */
public record NodeIdsReq(
        @NotEmpty(message = "必须关联至少一个知识点/子知识点") List<Long> nodeIds) {
}
