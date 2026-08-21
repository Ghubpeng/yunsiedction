package com.yunsie.module.learning.profile.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习档案对外最小契约（供未来 ai-tutor 上下文注入使用；本阶段不实现 AI 消费者）。
 * 只读最小必要字段视图；不保证实时（档案为事件异步/读时同步的派生数据）。
 * 其他域禁止直连 learning-profile 域 Entity/Mapper/表（模块边界铁律）。
 */
public interface LearnQueryApi {

    /** 学习历史汇总（null=该用户暂无档案） */
    SummaryView findSummary(Long userId);

    /** 指定证书当前版本的掌握度视图（空列表=无） */
    List<MasteryView> listMastery(Long userId, Long certificateId);

    /** 薄弱点视图（按掌握度升序，最多 limit 条） */
    List<WeaknessView> listWeakness(Long userId, int limit);

    record SummaryView(Long userId, Long totalStudySeconds, Integer courseFinishedLessons,
                       Integer practiceCount, Integer practiceCorrectCount, Integer examCount,
                       BigDecimal examBestScore, Integer streakDays, LocalDate lastStudyDate) {
    }

    record MasteryView(Long nodeId, Long certificateId, Long versionId, Integer masteryValue,
                       Integer correctCount, Integer wrongCount, LocalDateTime lastPracticeAt) {
    }

    record WeaknessView(Long nodeId, Integer masteryValue, Integer wrongCount,
                        Integer practiceCount, LocalDateTime lastPracticeAt) {
    }
}
