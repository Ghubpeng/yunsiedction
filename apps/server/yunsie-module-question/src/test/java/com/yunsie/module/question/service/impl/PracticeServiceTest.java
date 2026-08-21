package com.yunsie.module.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.question.dto.PracticeSubmitReq;
import com.yunsie.module.question.entity.Question;
import com.yunsie.module.question.enums.QuestionStatus;
import com.yunsie.module.question.enums.QuestionType;
import com.yunsie.module.question.error.QuestionErrorCode;
import com.yunsie.module.question.mapper.QuestionKnowledgeNodeMapper;
import com.yunsie.module.question.mapper.QuestionMapper;
import com.yunsie.module.question.mapper.QuestionMistakeMapper;
import com.yunsie.module.question.mapper.QuestionOptionMapper;
import com.yunsie.module.question.mapper.QuestionPracticeRecordMapper;
import com.yunsie.module.question.service.GradingService;
import com.yunsie.module.question.service.MistakeService;
import com.yunsie.module.question.vo.PracticeSubmitResultVO;
import com.yunsie.module.subject.api.SubjectQueryApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 练习服务单元测试：判分 / 错题联动 / 模式参数校验 / 未发布不可练习。
 */
class PracticeServiceTest {

    private PracticeServiceImpl service(QuestionMapper questionMapper, MistakeService mistakeService,
                                        SubjectQueryApi subjectQueryApi) {
        return new PracticeServiceImpl(questionMapper, mock(QuestionOptionMapper.class),
                mock(QuestionKnowledgeNodeMapper.class), mock(QuestionMistakeMapper.class),
                mock(QuestionPracticeRecordMapper.class), new GradingService(), mistakeService, subjectQueryApi);
    }

    private Question publishedQuestion(Long id, int type, String answer) {
        Question q = new Question();
        q.setId(id);
        q.setCertificateId(100L);
        q.setQuestionType(type);
        q.setStem("题干");
        q.setAnalysis("解析");
        q.setAnswer(answer);
        q.setStatus(QuestionStatus.PUBLISHED.code());
        return q;
    }

    @Test
    void submit_correct_noMistake() {
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        when(questionMapper.selectById(10L)).thenReturn(publishedQuestion(10L, QuestionType.SINGLE_CHOICE.code(), "A"));
        MistakeService mistakeService = mock(MistakeService.class);

        PracticeSubmitResultVO result = service(questionMapper, mistakeService, mock(SubjectQueryApi.class))
                .submit(1L, new PracticeSubmitReq(10L, "a", 1, null, 100));

        assertTrue(result.correct());
        assertEquals("A", result.standardAnswer());
        verify(mistakeService).recordCorrect(1L, 10L);
    }

    @Test
    void submit_wrong_collectsMistake() {
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        when(questionMapper.selectById(10L)).thenReturn(publishedQuestion(10L, QuestionType.MULTIPLE_CHOICE.code(), "A|C"));
        MistakeService mistakeService = mock(MistakeService.class);

        PracticeSubmitResultVO result = service(questionMapper, mistakeService, mock(SubjectQueryApi.class))
                .submit(1L, new PracticeSubmitReq(10L, "C|A", 1, null, 100));

        // 顺序无关：C|A 标准化后 A|C → 正确
        assertTrue(result.correct());
        result = service(questionMapper, mistakeService, mock(SubjectQueryApi.class))
                .submit(1L, new PracticeSubmitReq(10L, "A", 1, null, 100));
        assertFalse(result.correct());
        verify(mistakeService).recordWrong(1L, 10L);
    }

    @Test
    void submit_notPublished_30303() {
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        Question draft = publishedQuestion(10L, QuestionType.SINGLE_CHOICE.code(), "A");
        draft.setStatus(QuestionStatus.DRAFT.code());
        when(questionMapper.selectById(10L)).thenReturn(draft);

        BizException ex = assertThrows(BizException.class,
                () -> service(questionMapper, mock(MistakeService.class), mock(SubjectQueryApi.class))
                        .submit(1L, new PracticeSubmitReq(10L, "A", 1, null, null)));
        assertEquals(QuestionErrorCode.QUESTION_NOT_PUBLISHED.code(), ex.getCode());
    }

    @Test
    void submit_invalidAnswer_30307() {
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        when(questionMapper.selectById(10L)).thenReturn(publishedQuestion(10L, QuestionType.TRUE_FALSE.code(), "T"));

        BizException ex = assertThrows(BizException.class,
                () -> service(questionMapper, mock(MistakeService.class), mock(SubjectQueryApi.class))
                        .submit(1L, new PracticeSubmitReq(10L, "maybe", 1, null, null)));
        assertEquals(QuestionErrorCode.ANSWER_INVALID.code(), ex.getCode());
    }

    @Test
    void next_sequence_requiresCertificate_30313() {
        BizException ex = assertThrows(BizException.class,
                () -> service(mock(QuestionMapper.class), mock(MistakeService.class), mock(SubjectQueryApi.class))
                        .next(1L, 1, null, null, 0L, 20, 1, 10));
        assertEquals(QuestionErrorCode.PRACTICE_MODE_INVALID.code(), ex.getCode());
    }

    @Test
    void next_knowledgePoint_invalidNode_30310() {
        SubjectQueryApi subjectQueryApi = mock(SubjectQueryApi.class);
        when(subjectQueryApi.findNode(200L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service(mock(QuestionMapper.class), mock(MistakeService.class), subjectQueryApi)
                        .next(1L, 3, null, 200L, null, 20, 1, 10));
        assertEquals(QuestionErrorCode.NODE_ASSOC_INVALID.code(), ex.getCode());
    }

    @Test
    void next_invalidMode_30313() {
        BizException ex = assertThrows(BizException.class,
                () -> service(mock(QuestionMapper.class), mock(MistakeService.class), mock(SubjectQueryApi.class))
                        .next(1L, 9, null, null, null, 20, 1, 10));
        assertEquals(QuestionErrorCode.PRACTICE_MODE_INVALID.code(), ex.getCode());
    }
}
