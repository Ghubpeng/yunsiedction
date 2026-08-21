package com.yunsie.module.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.exam.dto.SaveAnswersReq;
import com.yunsie.module.exam.entity.Exam;
import com.yunsie.module.exam.entity.ExamAnswer;
import com.yunsie.module.exam.entity.ExamAttempt;
import com.yunsie.module.exam.entity.ExamPaper;
import com.yunsie.module.exam.entity.ExamPaperQuestion;
import com.yunsie.module.exam.enums.AttemptStatus;
import com.yunsie.module.exam.enums.ExamStatus;
import com.yunsie.module.exam.enums.PaperStatus;
import com.yunsie.module.exam.error.ExamErrorCode;
import com.yunsie.module.exam.mapper.ExamAnswerMapper;
import com.yunsie.module.exam.mapper.ExamAttemptMapper;
import com.yunsie.module.exam.mapper.ExamMapper;
import com.yunsie.module.exam.mapper.ExamPaperMapper;
import com.yunsie.module.exam.mapper.ExamPaperOptionMapper;
import com.yunsie.module.exam.mapper.ExamPaperQuestionMapper;
import com.yunsie.module.exam.vo.AttemptStartVO;
import com.yunsie.module.exam.vo.SubmitResultVO;
import com.yunsie.module.question.api.QuestionQueryApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * attempt 服务单元测试：start 幂等/重考/暂存 upsert/超时/交卷幂等/条件更新/越权。
 */
class AttemptServiceTest {

    private AttemptServiceImpl service(ExamMapper examMapper, ExamPaperMapper paperMapper,
                                       ExamAttemptMapper attemptMapper, ExamAnswerMapper answerMapper,
                                       ExamPaperQuestionMapper pqMapper, QuestionQueryApi questionQueryApi) {
        return new AttemptServiceImpl(examMapper, paperMapper, pqMapper, mock(ExamPaperOptionMapper.class),
                attemptMapper, answerMapper, questionQueryApi, mock(ApplicationEventPublisher.class));
    }

    private Exam publishedExam() {
        Exam exam = new Exam();
        exam.setId(1L);
        exam.setName("模拟考试");
        exam.setStatus(ExamStatus.PUBLISHED.code());
        exam.setDurationMinutes(60);
        return exam;
    }

    private ExamPaper validPaper() {
        ExamPaper paper = new ExamPaper();
        paper.setId(10L);
        paper.setExamId(1L);
        paper.setStatus(PaperStatus.VALID.code());
        paper.setDurationMinutes(60);
        paper.setQuestionCount(2);
        return paper;
    }

    private ExamAttempt attempt(Long id, int status, LocalDateTime expiredAt) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(id);
        attempt.setExamId(1L);
        attempt.setPaperId(10L);
        attempt.setUserId(7L);
        attempt.setStatus(status);
        attempt.setStartedAt(LocalDateTime.now().minusMinutes(1));
        attempt.setExpiredAt(expiredAt);
        attempt.setQuestionCount(2);
        return attempt;
    }

    @Test
    void start_idempotent_returnsExistingInProgress() {
        ExamMapper examMapper = mock(ExamMapper.class);
        when(examMapper.selectById(1L)).thenReturn(publishedExam());
        ExamPaperMapper paperMapper = mock(ExamPaperMapper.class);
        when(paperMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(validPaper());
        ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
        ExamAttempt existing = attempt(55L, AttemptStatus.IN_PROGRESS.code(), LocalDateTime.now().plusHours(1));
        when(attemptMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        ExamPaperQuestionMapper pqMapper = mock(ExamPaperQuestionMapper.class);
        when(pqMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        AttemptStartVO vo = service(examMapper, paperMapper, attemptMapper, mock(ExamAnswerMapper.class),
                pqMapper, mock(QuestionQueryApi.class)).start(7L, 1L);

        assertEquals(55L, vo.attemptId());
        verify(attemptMapper, never()).insert(any(ExamAttempt.class));
    }

    @Test
    void start_retakeAfterSubmitted_createsNewAttempt() {
        ExamMapper examMapper = mock(ExamMapper.class);
        when(examMapper.selectById(1L)).thenReturn(publishedExam());
        ExamPaperMapper paperMapper = mock(ExamPaperMapper.class);
        when(paperMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(validPaper());
        ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
        when(attemptMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(inv -> {
            ((ExamAttempt) inv.getArgument(0)).setId(88L);
            return 1;
        }).when(attemptMapper).insert(any(ExamAttempt.class));
        ExamPaperQuestionMapper pqMapper = mock(ExamPaperQuestionMapper.class);
        when(pqMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        AttemptStartVO vo = service(examMapper, paperMapper, attemptMapper, mock(ExamAnswerMapper.class),
                pqMapper, mock(QuestionQueryApi.class)).start(7L, 1L);

        assertEquals(88L, vo.attemptId());
        assertEquals(AttemptStatus.IN_PROGRESS.code(), vo.status());
    }

    @Test
    void start_unpublishedExam_30410() {
        ExamMapper examMapper = mock(ExamMapper.class);
        Exam exam = publishedExam();
        exam.setStatus(ExamStatus.DRAFT.code());
        when(examMapper.selectById(1L)).thenReturn(exam);
        BizException ex = assertThrows(BizException.class,
                () -> service(examMapper, mock(ExamPaperMapper.class), mock(ExamAttemptMapper.class),
                        mock(ExamAnswerMapper.class), mock(ExamPaperQuestionMapper.class), mock(QuestionQueryApi.class))
                        .start(7L, 1L));
        assertEquals(ExamErrorCode.EXAM_NOT_AVAILABLE.code(), ex.getCode());
    }

    @Test
    void saveAnswers_upsertsExistingRow() {
        ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
        when(attemptMapper.selectById(55L))
                .thenReturn(attempt(55L, AttemptStatus.IN_PROGRESS.code(), LocalDateTime.now().plusHours(1)));
        ExamPaperQuestionMapper pqMapper = mock(ExamPaperQuestionMapper.class);
        ExamPaperQuestion pq = new ExamPaperQuestion();
        pq.setId(100L);
        pq.setPaperId(10L);
        when(pqMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(pq));
        ExamAnswerMapper answerMapper = mock(ExamAnswerMapper.class);
        ExamAnswer existing = new ExamAnswer();
        existing.setId(3L);
        when(answerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        service(mock(ExamMapper.class), mock(ExamPaperMapper.class), attemptMapper, answerMapper,
                pqMapper, mock(QuestionQueryApi.class))
                .saveAnswers(7L, 55L, new SaveAnswersReq(
                        List.of(new SaveAnswersReq.AnswerItem(100L, "A"))));

        verify(answerMapper).updateById(existing);
        assertEquals("A", existing.getSubmittedAnswer());
    }

    @Test
    void saveAnswers_expired_30414() {
        ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
        when(attemptMapper.selectById(55L))
                .thenReturn(attempt(55L, AttemptStatus.IN_PROGRESS.code(), LocalDateTime.now().minusMinutes(1)));
        when(attemptMapper.selectById(anyLong()))
                .thenReturn(attempt(55L, AttemptStatus.IN_PROGRESS.code(), LocalDateTime.now().minusMinutes(1)));

        BizException ex = assertThrows(BizException.class,
                () -> service(mock(ExamMapper.class), mock(ExamPaperMapper.class), attemptMapper,
                        mock(ExamAnswerMapper.class), mock(ExamPaperQuestionMapper.class), mock(QuestionQueryApi.class))
                        .saveAnswers(7L, 55L, new SaveAnswersReq(
                                List.of(new SaveAnswersReq.AnswerItem(100L, "A")))));
        assertEquals(ExamErrorCode.EXAM_EXPIRED.code(), ex.getCode());
    }

    @Test
    void saveAnswers_submitted_30415() {
        ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
        when(attemptMapper.selectById(55L))
                .thenReturn(attempt(55L, AttemptStatus.SUBMITTED.code(), LocalDateTime.now().plusHours(1)));

        BizException ex = assertThrows(BizException.class,
                () -> service(mock(ExamMapper.class), mock(ExamPaperMapper.class), attemptMapper,
                        mock(ExamAnswerMapper.class), mock(ExamPaperQuestionMapper.class), mock(QuestionQueryApi.class))
                        .saveAnswers(7L, 55L, new SaveAnswersReq(
                                List.of(new SaveAnswersReq.AnswerItem(100L, "A")))));
        assertEquals(ExamErrorCode.EXAM_ALREADY_SUBMITTED.code(), ex.getCode());
    }

    @Test
    void getAttempt_otherUser_30412() {
        ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
        ExamAttempt attempt = attempt(55L, AttemptStatus.IN_PROGRESS.code(), LocalDateTime.now().plusHours(1));
        attempt.setUserId(99L);
        when(attemptMapper.selectById(55L)).thenReturn(attempt);

        BizException ex = assertThrows(BizException.class,
                () -> service(mock(ExamMapper.class), mock(ExamPaperMapper.class), attemptMapper,
                        mock(ExamAnswerMapper.class), mock(ExamPaperQuestionMapper.class), mock(QuestionQueryApi.class))
                        .getAttempt(7L, 55L));
        assertEquals(ExamErrorCode.ATTEMPT_FORBIDDEN.code(), ex.getCode());
    }

    @Test
    void submit_idempotent_returnsExistingResult() {
        ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
        ExamAttempt submitted = attempt(55L, AttemptStatus.SUBMITTED.code(), LocalDateTime.now().minusHours(1));
        submitted.setScore(new BigDecimal("2.00"));
        submitted.setCorrectCount(2);
        when(attemptMapper.selectById(55L)).thenReturn(submitted);
        ExamMapper examMapper = mock(ExamMapper.class);
        when(examMapper.selectById(1L)).thenReturn(publishedExam());

        SubmitResultVO vo = service(examMapper, mock(ExamPaperMapper.class), attemptMapper,
                mock(ExamAnswerMapper.class), mock(ExamPaperQuestionMapper.class), mock(QuestionQueryApi.class))
                .submit(7L, 55L);

        assertEquals(new BigDecimal("2.00"), vo.score());
        verify(attemptMapper, never()).update(any(), any());
    }

    @Test
    void submit_conditionalUpdateZeroRows_returnsWinnerResult() {
        ExamMapper examMapper = mock(ExamMapper.class);
        when(examMapper.selectById(1L)).thenReturn(publishedExam());
        ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
        ExamAttempt inProgress = attempt(55L, AttemptStatus.IN_PROGRESS.code(), LocalDateTime.now().plusHours(1));
        ExamAttempt winner = attempt(55L, AttemptStatus.SUBMITTED.code(), LocalDateTime.now().plusHours(1));
        winner.setScore(new BigDecimal("0.00"));
        winner.setCorrectCount(0);
        // 顺序桩：第一次读取=进行中（归属校验），结算后读取=并发赢家的既有成绩
        when(attemptMapper.selectById(55L)).thenReturn(inProgress, winner);
        ExamPaperQuestionMapper pqMapper = mock(ExamPaperQuestionMapper.class);
        ExamPaperQuestion pq = new ExamPaperQuestion();
        pq.setId(100L);
        pq.setQuestionType(1);
        pq.setStandardAnswer("A");
        pq.setScore(new BigDecimal("1.00"));
        when(pqMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(pq));
        ExamAnswerMapper answerMapper = mock(ExamAnswerMapper.class);
        when(answerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        QuestionQueryApi questionQueryApi = mock(QuestionQueryApi.class);
        when(questionQueryApi.gradeSubmission(1, "A", "")).thenReturn(false);

        // 条件更新返回 0：已被并发交卷 → 直接返回既有成绩，不重复计分
        when(attemptMapper.update(any(ExamAttempt.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(0);

        SubmitResultVO vo = service(examMapper, mock(ExamPaperMapper.class), attemptMapper, answerMapper,
                pqMapper, questionQueryApi).submit(7L, 55L);

        assertEquals(new BigDecimal("0.00"), vo.score());
    }

    @Test
    void submit_gradesViaContract_threeTypes() {
        ExamMapper examMapper = mock(ExamMapper.class);
        when(examMapper.selectById(1L)).thenReturn(publishedExam());
        ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
        ExamAttempt inProgress = attempt(55L, AttemptStatus.IN_PROGRESS.code(), LocalDateTime.now().plusHours(1));
        ExamAttempt finalAttempt = attempt(55L, AttemptStatus.SUBMITTED.code(), LocalDateTime.now().plusHours(1));
        finalAttempt.setScore(new BigDecimal("2.00"));
        finalAttempt.setCorrectCount(2);
        // 顺序桩：第一次=进行中（归属校验），结算后=已交卷最终状态
        when(attemptMapper.selectById(55L)).thenReturn(inProgress, finalAttempt);
        ExamPaperQuestionMapper pqMapper = mock(ExamPaperQuestionMapper.class);
        ExamPaperQuestion q1 = new ExamPaperQuestion();
        q1.setId(101L);
        q1.setQuestionType(1);
        q1.setStandardAnswer("A");
        q1.setScore(new BigDecimal("1.00"));
        ExamPaperQuestion q2 = new ExamPaperQuestion();
        q2.setId(102L);
        q2.setQuestionType(2);
        q2.setStandardAnswer("A|C");
        q2.setScore(new BigDecimal("1.00"));
        when(pqMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(q1, q2));
        ExamAnswerMapper answerMapper = mock(ExamAnswerMapper.class);
        ExamAnswer a1 = new ExamAnswer();
        a1.setPaperQuestionId(101L);
        a1.setSubmittedAnswer("A");
        ExamAnswer a2 = new ExamAnswer();
        a2.setPaperQuestionId(102L);
        a2.setSubmittedAnswer("C|A");
        when(answerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(a1, a2));
        QuestionQueryApi questionQueryApi = mock(QuestionQueryApi.class);
        when(questionQueryApi.gradeSubmission(1, "A", "A")).thenReturn(true);
        when(questionQueryApi.gradeSubmission(2, "A|C", "C|A")).thenReturn(true);
        when(attemptMapper.update(any(ExamAttempt.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(1);

        SubmitResultVO vo = service(examMapper, mock(ExamPaperMapper.class), attemptMapper, answerMapper,
                pqMapper, questionQueryApi).submit(7L, 55L);

        assertEquals(new BigDecimal("2.00"), vo.score());
        assertEquals(2, vo.correctCount());
        ArgumentCaptor<ExamAttempt> captor = ArgumentCaptor.forClass(ExamAttempt.class);
        verify(attemptMapper).update(captor.capture(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        assertEquals(AttemptStatus.SUBMITTED.code(), captor.getValue().getStatus());
        assertEquals(new BigDecimal("2.00"), captor.getValue().getScore());
    }
}
