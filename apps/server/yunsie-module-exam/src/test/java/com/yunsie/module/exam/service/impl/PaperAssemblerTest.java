package com.yunsie.module.exam.service.impl;

import com.yunsie.common.exception.BizException;
import com.yunsie.module.exam.entity.Exam;
import com.yunsie.module.exam.error.ExamErrorCode;
import com.yunsie.module.exam.mapper.ExamPaperMapper;
import com.yunsie.module.exam.mapper.ExamPaperOptionMapper;
import com.yunsie.module.exam.mapper.ExamPaperQuestionMapper;
import com.yunsie.module.exam.service.PaperShuffler;
import com.yunsie.module.question.api.QuestionQueryApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 组卷装配器单元测试：种子可复现 / 同卷不重复 / 过滤 / 候选不足失败 / 快照字段完整。
 */
class PaperAssemblerTest {

    private PaperAssemblerImpl assembler(QuestionQueryApi questionQueryApi, ExamPaperMapper paperMapper) {
        return new PaperAssemblerImpl(paperMapper, mock(ExamPaperQuestionMapper.class),
                mock(ExamPaperOptionMapper.class), questionQueryApi, new PaperShuffler());
    }

    private Exam exam() {
        Exam exam = new Exam();
        exam.setId(1L);
        exam.setCertificateId(100L);
        exam.setName("模拟考试");
        exam.setDurationMinutes(60);
        return exam;
    }

    private QuestionQueryApi.QuestionSnapshot snapshot(Long id, int type, String answer, int difficulty) {
        return new QuestionQueryApi.QuestionSnapshot(id, 100L, type, "题干" + id, answer, "解析" + id,
                difficulty, 2, 1,
                List.of(new QuestionQueryApi.OptionSnapshot("A", "选项A", 0),
                        new QuestionQueryApi.OptionSnapshot("B", "选项B", 1)),
                List.of(200L + id));
    }

    @Test
    void seed_reproducible_sameSeedSameOrder() {
        PaperShuffler shuffler = new PaperShuffler();
        List<Integer> items = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertEquals(shuffler.shuffle(items, 42L), shuffler.shuffle(items, 42L));
        assertFalse(shuffler.shuffle(items, 42L).equals(shuffler.shuffle(items, 43L)));
    }

    @Test
    void assemble_candidatesInsufficient_30419() {
        QuestionQueryApi questionQueryApi = mock(QuestionQueryApi.class);
        when(questionQueryApi.findPublishedQuestions(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(snapshot(1L, 1, "A", 2)));

        BizException ex = assertThrows(BizException.class,
                () -> assembler(questionQueryApi, mock(ExamPaperMapper.class))
                        .assemble(exam(), new com.yunsie.module.exam.dto.ExamCreateReq.AssembleRule(
                                5, List.of(1, 2, 3), null, null)));
        assertEquals(ExamErrorCode.EXAMS_INSUFFICIENT_NEED.code(), ex.getCode());
    }

    @Test
    void assemble_success_snapshotCompleteAndUnique() {
        QuestionQueryApi questionQueryApi = mock(QuestionQueryApi.class);
        when(questionQueryApi.findPublishedQuestions(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(snapshot(1L, 1, "A", 2), snapshot(2L, 2, "A|C", 3), snapshot(3L, 3, "T", 1)));
        ExamPaperMapper paperMapper = mock(ExamPaperMapper.class);
        ExamPaperQuestionMapper pqMapper = mock(ExamPaperQuestionMapper.class);
        ExamPaperOptionMapper poMapper = mock(ExamPaperOptionMapper.class);
        PaperAssemblerImpl assembler = new PaperAssemblerImpl(paperMapper, pqMapper, poMapper,
                questionQueryApi, new PaperShuffler());
        org.mockito.stubbing.Answer<Integer> paperIdAnswer = inv -> {
            ((com.yunsie.module.exam.entity.ExamPaper) inv.getArgument(0)).setId(9L);
            return 1;
        };
        org.mockito.stubbing.Answer<Integer> pqIdAnswer = inv -> {
            ((com.yunsie.module.exam.entity.ExamPaperQuestion) inv.getArgument(0)).setId(90L);
            return 1;
        };
        doAnswer(paperIdAnswer).when(paperMapper).insert(any(com.yunsie.module.exam.entity.ExamPaper.class));
        doAnswer(pqIdAnswer).when(pqMapper).insert(any(com.yunsie.module.exam.entity.ExamPaperQuestion.class));

        Long paperId = assembler.assemble(exam(),
                new com.yunsie.module.exam.dto.ExamCreateReq.AssembleRule(3, List.of(1, 2, 3), null, null));

        assertEquals(9L, paperId);
        ArgumentCaptor<com.yunsie.module.exam.entity.ExamPaperQuestion> pqCaptor =
                ArgumentCaptor.forClass(com.yunsie.module.exam.entity.ExamPaperQuestion.class);
        verify(pqMapper, times(3)).insert(pqCaptor.capture());
        List<com.yunsie.module.exam.entity.ExamPaperQuestion> questions = pqCaptor.getAllValues();
        assertEquals(List.of(1, 2, 3), questions.stream().map(com.yunsie.module.exam.entity.ExamPaperQuestion::getSort).toList());
        assertEquals(3, questions.stream().map(com.yunsie.module.exam.entity.ExamPaperQuestion::getQuestionId).distinct().count());
        for (com.yunsie.module.exam.entity.ExamPaperQuestion q : questions) {
            assertEquals(new java.math.BigDecimal("1.00"), q.getScore());
            assertFalse(q.getStem().isBlank());
            assertFalse(q.getAnalysis().isBlank());
            assertFalse(q.getStandardAnswer().isBlank());
            assertFalse(q.getNodeIds().isBlank());
            assertEquals(1, q.getContentVersion());
        }
        verify(poMapper, times(6)).insert(any(com.yunsie.module.exam.entity.ExamPaperOption.class));
    }

    @Test
    void assemble_difficultyFilter_shrinksPool() {
        QuestionQueryApi questionQueryApi = mock(QuestionQueryApi.class);
        when(questionQueryApi.findPublishedQuestions(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(snapshot(1L, 1, "A", 1), snapshot(2L, 1, "B", 2), snapshot(3L, 1, "C", 2)));

        BizException ex = assertThrows(BizException.class,
                () -> assembler(questionQueryApi, mock(ExamPaperMapper.class))
                        .assemble(exam(), new com.yunsie.module.exam.dto.ExamCreateReq.AssembleRule(
                                3, List.of(1), null, 2)));
        assertEquals(ExamErrorCode.EXAMS_INSUFFICIENT_NEED.code(), ex.getCode());
    }

    @Test
    void assemble_voidsOldValidPaper() {
        QuestionQueryApi questionQueryApi = mock(QuestionQueryApi.class);
        when(questionQueryApi.findPublishedQuestions(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(snapshot(1L, 1, "A", 2)));
        ExamPaperMapper paperMapper = mock(ExamPaperMapper.class);
        ExamPaperQuestionMapper pqMapper = mock(ExamPaperQuestionMapper.class);
        ExamPaperOptionMapper poMapper = mock(ExamPaperOptionMapper.class);
        PaperAssemblerImpl assembler = new PaperAssemblerImpl(paperMapper, pqMapper, poMapper,
                questionQueryApi, new PaperShuffler());
        doAnswer(inv -> {
            ((com.yunsie.module.exam.entity.ExamPaper) inv.getArgument(0)).setId(9L);
            return 1;
        }).when(paperMapper).insert(any(com.yunsie.module.exam.entity.ExamPaper.class));
        doAnswer(inv -> {
            ((com.yunsie.module.exam.entity.ExamPaperQuestion) inv.getArgument(0)).setId(90L);
            return 1;
        }).when(pqMapper).insert(any(com.yunsie.module.exam.entity.ExamPaperQuestion.class));

        assembler.assemble(exam(), new com.yunsie.module.exam.dto.ExamCreateReq.AssembleRule(
                1, List.of(1), null, null));
        verify(paperMapper).update(any(), any());
    }
}
