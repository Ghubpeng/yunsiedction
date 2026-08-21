package com.yunsie.module.learning.profile.vo;

import java.time.LocalDate;

/**
 * 周学习报告（Stage 2.4；规则聚合真实数据，无 AI 生成内容）。
 * 掌握变化说明：平台无历史掌握度快照，masteredNodes 定义为
 * 「本周练习过且当前掌握度 ≥60 的知识点数」，前端如实标注口径。
 */
public record WeeklyReportVO(
        LocalDate startDate,
        LocalDate endDate,
        Long studySeconds,
        Integer finishedChapters,
        Integer practiceCount,
        Integer practiceCorrectCount,
        Integer masteredNodes,
        Integer practicedNodes) {
}
