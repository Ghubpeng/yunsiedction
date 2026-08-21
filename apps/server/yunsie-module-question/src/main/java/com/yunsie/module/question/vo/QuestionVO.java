package com.yunsie.module.question.vo;

import java.util.List;

/**
 * 题目 VO（管理端列表）。
 */
public record QuestionVO(
        Long id,
        Long certificateId,
        Integer questionType,
        String stem,
        Integer difficulty,
        Integer source,
        Integer status,
        Integer contentVersion,
        String rejectReason,
        Long auditBy) {
}
