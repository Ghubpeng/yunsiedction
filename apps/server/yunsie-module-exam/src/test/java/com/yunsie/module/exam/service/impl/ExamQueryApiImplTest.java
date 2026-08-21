package com.yunsie.module.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.exam.api.ExamQueryApi;
import com.yunsie.module.exam.entity.Exam;
import com.yunsie.module.exam.entity.ExamAttempt;
import com.yunsie.module.exam.enums.AttemptStatus;
import com.yunsie.module.exam.enums.ExamStatus;
import com.yunsie.module.exam.mapper.ExamAttemptMapper;
import com.yunsie.module.exam.mapper.ExamMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stage 1.6 契约扩展测试：listSubmittedAttempts / findExam 的 View 映射（追加式，不改既有方法）。
 * 过滤条件（status=已交卷）在 WHERE 子句，经 wrapper 断言 + boot 集成测试（真实 MySQL）共同保证。
 */
class ExamQueryApiImplTest {

    private final ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    private final ExamMapper examMapper = mock(ExamMapper.class);

    private ExamQueryApiImpl api() {
        return new ExamQueryApiImpl(attemptMapper, examMapper);
    }

    private ExamAttempt attempt(long id, long userId, int status) {
        ExamAttempt a = new ExamAttempt();
        a.setId(id);
        a.setExamId(5L);
        a.setUserId(userId);
        a.setStatus(status);
        a.setScore(new BigDecimal("77.50"));
        a.setCorrectCount(7);
        a.setQuestionCount(10);
        a.setStartedAt(LocalDateTime.of(2026, 8, 19, 9, 0));
        a.setSubmittedAt(LocalDateTime.of(2026, 8, 19, 9, 30));
        return a;
    }

    @Test
    void listSubmittedAttempts_mapsViews() {
        when(attemptMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(attempt(1L, 10L, AttemptStatus.SUBMITTED.code()),
                        attempt(2L, 10L, AttemptStatus.SUBMITTED.code())));

        List<ExamQueryApi.AttemptSummaryView> views = api().listSubmittedAttempts(10L);

        // status=2 过滤与倒序在 WHERE 子句，由 boot 集成测试（真实 MySQL）覆盖
        assertEquals(2, views.size());
        ExamQueryApi.AttemptSummaryView v = views.get(0);
        assertEquals(1L, v.attemptId());
        assertEquals(5L, v.examId());
        assertEquals(new BigDecimal("77.50"), v.score());
        assertEquals(7, v.correctCount());
        assertEquals(10, v.questionCount());
        assertEquals(LocalDateTime.of(2026, 8, 19, 9, 0), v.startedAt());
        assertEquals(LocalDateTime.of(2026, 8, 19, 9, 30), v.submittedAt());
    }

    @Test
    void listSubmittedAttempts_nullUserId_empty() {
        assertTrue(api().listSubmittedAttempts(null).isEmpty());
    }

    @Test
    void findExam_mapsView() {
        Exam exam = new Exam();
        exam.setId(5L);
        exam.setCertificateId(9L);
        exam.setName("模拟考试");
        exam.setPassScore(new BigDecimal("60"));
        exam.setStatus(2);
        when(examMapper.selectById(5L)).thenReturn(exam);

        ExamQueryApi.ExamView view = api().findExam(5L);

        assertEquals(5L, view.id());
        assertEquals(9L, view.certificateId());
        assertEquals("模拟考试", view.name());
        assertEquals(new BigDecimal("60"), view.passScore());
    }

    @Test
    void findExam_notFound_null() {
        when(examMapper.selectById(999L)).thenReturn(null);
        assertNull(api().findExam(999L));
    }

    @Test
    void existingMethod_findSubmittedAttempt_unchanged() {
        when(attemptMapper.selectById(3L)).thenReturn(attempt(3L, 10L, AttemptStatus.SUBMITTED.code()));
        ExamQueryApi.AttemptView view = api().findSubmittedAttempt(3L);
        assertEquals(3L, view.attemptId());
        assertEquals(10L, view.userId());
        // 未交卷 → null（既有行为不变）
        when(attemptMapper.selectById(4L)).thenReturn(attempt(4L, 10L, AttemptStatus.IN_PROGRESS.code()));
        assertNull(api().findSubmittedAttempt(4L));
    }

    // ---------- Stage 1.7 追加：notify 开考提醒契约 ----------

    @Test
    void listExamsOpeningSoon_mapsViews() {
        Exam exam = new Exam();
        exam.setId(7L);
        exam.setCertificateId(9L);
        exam.setName("E2E Exam");
        exam.setStatus(ExamStatus.PUBLISHED.code());
        exam.setValidFrom(LocalDateTime.of(2026, 8, 21, 9, 0));
        when(examMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(exam));

        List<ExamQueryApi.OpeningExamView> views = api().listExamsOpeningSoon(
                LocalDateTime.of(2026, 8, 20, 0, 0), LocalDateTime.of(2026, 8, 22, 0, 0));

        assertEquals(1, views.size());
        assertEquals(7L, views.get(0).id());
        assertEquals(9L, views.get(0).certificateId());
        assertEquals("E2E Exam", views.get(0).name());
        assertEquals(LocalDateTime.of(2026, 8, 21, 9, 0), views.get(0).validFrom());
        // 过滤条件（status=已发布/valid_from 区间）在 WHERE 子句，由 boot 集成测试（真实 MySQL）覆盖
    }

    @Test
    void listExamsOpeningSoon_nullRange_empty() {
        assertTrue(api().listExamsOpeningSoon(null, null).isEmpty());
    }

    @Test
    void listLearnerIdsByCertificate_distinctSubmittedUsers() {
        Exam exam = new Exam();
        exam.setId(7L);
        exam.setCertificateId(9L);
        when(examMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(exam));
        // 模拟 SQL 过滤后的已交卷结果（status 条件在 WHERE 子句，由 boot 集成测试覆盖）：
        // 用户 10 两条交卷 + 用户 11 一条交卷 → 去重后 [10, 11]
        when(attemptMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(attempt(1L, 10L, AttemptStatus.SUBMITTED.code()),
                        attempt(2L, 10L, AttemptStatus.SUBMITTED.code()),
                        attempt(3L, 11L, AttemptStatus.SUBMITTED.code())));

        List<Long> learnerIds = api().listLearnerIdsByCertificate(9L);

        assertEquals(2, learnerIds.size());
        assertTrue(learnerIds.contains(10L));
        assertTrue(learnerIds.contains(11L));
    }

    @Test
    void listLearnerIdsByCertificate_nullCert_empty() {
        assertTrue(api().listLearnerIdsByCertificate(null).isEmpty());
    }
}
