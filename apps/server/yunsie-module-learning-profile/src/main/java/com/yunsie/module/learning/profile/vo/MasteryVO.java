package com.yunsie.module.learning.profile.vo;

import java.time.LocalDateTime;

/**
 * 知识点掌握度视图（含节点名称/路径便于按知识树展示）。
 */
public record MasteryVO(
        Long nodeId,
        String nodeName,
        String nodeCode,
        String nodePath,
        Integer nodeType,
        Long certificateId,
        Long subjectId,
        Long versionId,
        Integer masteryValue,
        Integer correctCount,
        Integer wrongCount,
        LocalDateTime lastPracticeAt) {
}
