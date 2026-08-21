package com.yunsie.module.exam.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * exam 域对外最小契约（供后续 learning-profile 等域使用）。
 * 其他域禁止直连 exam 域 Entity/Mapper/表（模块边界铁律）。
 *
 * <p>Stage 1.6 追加式扩展（仅新增方法/View，不改已有方法行为）：
 * {@link #listSubmittedAttempts(Long)} 与 {@link #findExam(Long)} 供 learning-profile 聚合。</p>
 */
public interface ExamQueryApi {

    /** 已交卷 attempt 摘要（null=不存在或未交卷） */
    AttemptView findSubmittedAttempt(Long attemptId);

    /** 用户的全部已交卷 attempt 摘要（按交卷时间倒序；空列表=无） */
    List<AttemptSummaryView> listSubmittedAttempts(Long userId);

    /** 考试视图（任意状态；null=不存在或已逻辑删除；prediction 用 pass_score） */
    ExamView findExam(Long examId);

    /** 已发布且开考时间(valid_from)落在 [from, to] 的考试（notify 开考提醒扫描；空列表=无） */
    List<OpeningExamView> listExamsOpeningSoon(LocalDateTime from, LocalDateTime to);

    /** 该证书下有过已交卷考试记录的学员 ID 去重列表（notify 开考提醒定向；空列表=无） */
    List<Long> listLearnerIdsByCertificate(Long certificateId);

    /** 归属该科目的考试数（Stage 2.3A 科目删除守卫） */
    long countBySubject(Long subjectId);

    record AttemptView(
            Long attemptId,
            Long userId,
            Long examId,
            Integer status,
            BigDecimal score,
            Integer correctCount,
            Integer questionCount,
            LocalDateTime submittedAt) {
    }

    record AttemptSummaryView(
            Long attemptId,
            Long examId,
            BigDecimal score,
            Integer correctCount,
            Integer questionCount,
            LocalDateTime startedAt,
            LocalDateTime submittedAt) {
    }

    record ExamView(
            Long id,
            Long certificateId,
            String name,
            BigDecimal passScore,
            Integer status) {
    }

    record OpeningExamView(
            Long id,
            Long certificateId,
            String name,
            LocalDateTime validFrom) {
    }
}
