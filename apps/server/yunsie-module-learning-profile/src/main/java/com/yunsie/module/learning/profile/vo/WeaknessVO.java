package com.yunsie.module.learning.profile.vo;

import java.time.LocalDateTime;

/**
 * 薄弱点视图（读时派生：掌握度低 + 错题多 + 最近练习时间；不落库）。
 */
public record WeaknessVO(
        Long nodeId,
        String nodeName,
        String nodePath,
        Integer nodeType,
        Integer mastery,
        Integer wrongCount,
        Integer practiceCount,
        LocalDateTime lastPracticeAt) {
}
