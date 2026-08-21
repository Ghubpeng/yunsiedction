package com.yunsie.module.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.exam.api.ExamQueryApi;
import com.yunsie.module.exam.entity.Exam;
import com.yunsie.module.exam.entity.ExamAttempt;
import com.yunsie.module.exam.enums.AttemptStatus;
import com.yunsie.module.exam.enums.ExamStatus;
import com.yunsie.module.exam.mapper.ExamAttemptMapper;
import com.yunsie.module.exam.mapper.ExamMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * exam 域对外最小契约实现（不可变 View DTO）。
 * Stage 1.6 追加：listSubmittedAttempts / findExam（仅新增，不改既有方法行为）。
 * Stage 1.7 追加：listExamsOpeningSoon / listLearnerIdsByCertificate（notify 开考提醒，仅新增）。
 */
@Service
@RequiredArgsConstructor
public class ExamQueryApiImpl implements ExamQueryApi {

    private final ExamAttemptMapper attemptMapper;
    private final ExamMapper examMapper;

    @Override
    public AttemptView findSubmittedAttempt(Long attemptId) {
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null || AttemptStatus.of(attempt.getStatus()) != AttemptStatus.SUBMITTED) {
            return null;
        }
        return new AttemptView(attempt.getId(), attempt.getUserId(), attempt.getExamId(), attempt.getStatus(),
                attempt.getScore(), attempt.getCorrectCount(), attempt.getQuestionCount(), attempt.getSubmittedAt());
    }

    @Override
    public List<AttemptSummaryView> listSubmittedAttempts(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return attemptMapper.selectList(new LambdaQueryWrapper<ExamAttempt>()
                        .eq(ExamAttempt::getUserId, userId)
                        .eq(ExamAttempt::getStatus, AttemptStatus.SUBMITTED.code())
                        .orderByDesc(ExamAttempt::getSubmittedAt))
                .stream()
                .map(a -> new AttemptSummaryView(a.getId(), a.getExamId(), a.getScore(),
                        a.getCorrectCount(), a.getQuestionCount(), a.getStartedAt(), a.getSubmittedAt()))
                .toList();
    }

    @Override
    public ExamView findExam(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            return null;
        }
        return new ExamView(exam.getId(), exam.getCertificateId(), exam.getName(),
                exam.getPassScore(), exam.getStatus());
    }

    @Override
    public List<OpeningExamView> listExamsOpeningSoon(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return List.of();
        }
        return examMapper.selectList(new LambdaQueryWrapper<Exam>()
                        .eq(Exam::getStatus, ExamStatus.PUBLISHED.code())
                        .isNotNull(Exam::getValidFrom)
                        .ge(Exam::getValidFrom, from)
                        .le(Exam::getValidFrom, to))
                .stream()
                .map(e -> new OpeningExamView(e.getId(), e.getCertificateId(), e.getName(), e.getValidFrom()))
                .toList();
    }

    @Override
    public List<Long> listLearnerIdsByCertificate(Long certificateId) {
        if (certificateId == null) {
            return List.of();
        }
        List<Long> examIds = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                        .eq(Exam::getCertificateId, certificateId))
                .stream().map(Exam::getId).toList();
        if (examIds.isEmpty()) {
            return List.of();
        }
        return attemptMapper.selectList(new LambdaQueryWrapper<ExamAttempt>()
                        .in(ExamAttempt::getExamId, examIds)
                        .eq(ExamAttempt::getStatus, AttemptStatus.SUBMITTED.code()))
                .stream().map(ExamAttempt::getUserId).distinct().toList();
    }

    @Override
    public long countBySubject(Long subjectId) {
        if (subjectId == null) {
            return 0;
        }
        Long count = examMapper.selectCount(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getSubjectId, subjectId));
        return count == null ? 0 : count;
    }
}
