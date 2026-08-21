package com.yunsie.module.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.question.dto.QuestionCreateReq;
import com.yunsie.module.question.dto.QuestionUpdateReq;
import com.yunsie.module.question.entity.Question;
import com.yunsie.module.question.enums.QuestionStatus;
import com.yunsie.module.question.enums.QuestionType;
import com.yunsie.module.question.error.QuestionErrorCode;
import com.yunsie.module.question.mapper.QuestionKnowledgeNodeMapper;
import com.yunsie.module.question.mapper.QuestionMapper;
import com.yunsie.module.question.mapper.QuestionOptionMapper;
import com.yunsie.module.question.service.GradingService;
import com.yunsie.module.subject.api.SubjectQueryApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 题目服务单元测试：状态机 / 选项答案校验 / 知识点校验 / 版本与重审。
 */
class QuestionServiceTest {

    private static final Long CERT_ID = 100L;
    private static final Long NODE_ID = 200L;

    private QuestionServiceImpl service(QuestionMapper questionMapper, SubjectQueryApi subjectQueryApi,
                                        CertificateQueryApi certificateQueryApi) {
        return new QuestionServiceImpl(questionMapper, mock(QuestionOptionMapper.class),
                mock(QuestionKnowledgeNodeMapper.class), new GradingService(), subjectQueryApi, certificateQueryApi);
    }

    private QuestionServiceImpl fullService(QuestionMapper questionMapper, QuestionOptionMapper optionMapper,
                                            QuestionKnowledgeNodeMapper nodeMapper, SubjectQueryApi subjectQueryApi,
                                            CertificateQueryApi certificateQueryApi) {
        return new QuestionServiceImpl(questionMapper, optionMapper, nodeMapper, new GradingService(),
                subjectQueryApi, certificateQueryApi);
    }

    private SubjectQueryApi validNodeApi() {
        SubjectQueryApi api = mock(SubjectQueryApi.class);
        when(api.findNode(NODE_ID)).thenReturn(new SubjectQueryApi.KnowledgeNodeView(
                NODE_ID, 1L, CERT_ID, 1L, 2, "无菌技术", "KP1", "/1/2/", 2, 1));
        return api;
    }

    private CertificateQueryApi validCertApi() {
        CertificateQueryApi api = mock(CertificateQueryApi.class);
        when(api.findCertificate(CERT_ID))
                .thenReturn(new CertificateQueryApi.CertificateView(CERT_ID, 1L, "护士执业资格", "nurse", "", 1));
        return api;
    }

    private Question question(Long id, int status, int contentVersion) {
        Question q = new Question();
        q.setId(id);
        q.setCertificateId(CERT_ID);
        q.setQuestionType(QuestionType.SINGLE_CHOICE.code());
        q.setStem("题干" + id);
        q.setAnalysis("解析");
        q.setAnswer("A");
        q.setStatus(status);
        q.setContentVersion(contentVersion);
        return q;
    }

    private QuestionCreateReq createReq(String answer, List<QuestionCreateReq.OptionItem> options) {
        return new QuestionCreateReq(CERT_ID, QuestionType.SINGLE_CHOICE.code(), "题干", "解析", answer,
                2, 2, options, List.of(NODE_ID));
    }

    private static List<QuestionCreateReq.OptionItem> abOptions() {
        return List.of(new QuestionCreateReq.OptionItem("A", "选项A"), new QuestionCreateReq.OptionItem("B", "选项B"));
    }

    @Test
    void create_success() {
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        when(questionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doAnswer(inv -> {
            ((Question) inv.getArgument(0)).setId(1L);
            return 1;
        }).when(questionMapper).insert(any(Question.class));
        QuestionServiceImpl service = fullService(questionMapper, mock(QuestionOptionMapper.class),
                mock(QuestionKnowledgeNodeMapper.class), validNodeApi(), validCertApi());

        Long id = service.create(createReq("A", abOptions()));
        assertEquals(1L, id);
        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionMapper).insert(captor.capture());
        assertEquals(QuestionStatus.DRAFT.code(), captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getContentVersion());
    }

    @Test
    void create_answerNotInOptions_30307() {
        BizException ex = assertThrows(BizException.class,
                () -> service(mock(QuestionMapper.class), validNodeApi(), validCertApi())
                        .create(createReq("C", abOptions())));
        assertEquals(QuestionErrorCode.ANSWER_INVALID.code(), ex.getCode());
    }

    @Test
    void create_trueFalseWithOptions_30309() {
        QuestionCreateReq req = new QuestionCreateReq(CERT_ID, QuestionType.TRUE_FALSE.code(),
                "判断题干", "解析", "T", 2, 2, abOptions(), List.of(NODE_ID));
        BizException ex = assertThrows(BizException.class,
                () -> service(mock(QuestionMapper.class), validNodeApi(), validCertApi()).create(req));
        assertEquals(QuestionErrorCode.OPTION_INVALID.code(), ex.getCode());
    }

    @Test
    void create_invalidNode_30310() {
        SubjectQueryApi api = mock(SubjectQueryApi.class);
        when(api.findNode(NODE_ID)).thenReturn(new SubjectQueryApi.KnowledgeNodeView(
                NODE_ID, 1L, CERT_ID, 1L, 1, "章节", "CH1", "/1/", 1, 1)); // 章节类型非法
        BizException ex = assertThrows(BizException.class,
                () -> service(mock(QuestionMapper.class), api, validCertApi())
                        .create(createReq("A", abOptions())));
        assertEquals(QuestionErrorCode.NODE_ASSOC_INVALID.code(), ex.getCode());
    }

    @Test
    void create_duplicateStem_30314() {
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        when(questionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        BizException ex = assertThrows(BizException.class,
                () -> service(questionMapper, validNodeApi(), validCertApi()).create(createReq("A", abOptions())));
        assertEquals(QuestionErrorCode.DUPLICATE_QUESTION.code(), ex.getCode());
    }

    @Test
    void approve_draft_30306() {
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        when(questionMapper.selectById(1L)).thenReturn(question(1L, QuestionStatus.DRAFT.code(), 1));
        BizException ex = assertThrows(BizException.class,
                () -> service(questionMapper, validNodeApi(), validCertApi()).approve(1L, 9L));
        assertEquals(QuestionErrorCode.QUESTION_STATUS_INVALID_ACTION.code(), ex.getCode());
    }

    @Test
    void update_published_bumpsVersionAndBackToDraft() {
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        Question published = question(1L, QuestionStatus.PUBLISHED.code(), 3);
        when(questionMapper.selectById(1L)).thenReturn(published);
        when(questionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        QuestionOptionMapper optionMapper = mock(QuestionOptionMapper.class);
        QuestionServiceImpl full = fullService(questionMapper, optionMapper,
                mock(QuestionKnowledgeNodeMapper.class), validNodeApi(), validCertApi());
        full.update(1L, new QuestionUpdateReq("新题干", "解析", "A", 2, 2, abOptions()));

        assertEquals(4, published.getContentVersion());
        assertEquals(QuestionStatus.DRAFT.code(), published.getStatus());
        verify(questionMapper).updateById(published);
    }

    @Test
    void update_pending_30304() {
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        when(questionMapper.selectById(1L)).thenReturn(question(1L, QuestionStatus.PENDING_REVIEW.code(), 1));
        BizException ex = assertThrows(BizException.class,
                () -> service(questionMapper, validNodeApi(), validCertApi())
                        .update(1L, new QuestionUpdateReq("新题干", "解析", "A", 2, 2, abOptions())));
        assertEquals(QuestionErrorCode.QUESTION_STATUS_NOT_EDITABLE.code(), ex.getCode());
    }

    @Test
    void delete_published_goesRecycled() {
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        Question published = question(1L, QuestionStatus.PUBLISHED.code(), 1);
        when(questionMapper.selectById(1L)).thenReturn(published);
        service(questionMapper, validNodeApi(), validCertApi()).delete(1L);
        assertEquals(QuestionStatus.RECYCLED.code(), published.getStatus());
        verify(questionMapper).updateById(published);
    }

    @Test
    void delete_pendingReview_30318() {
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        when(questionMapper.selectById(1L)).thenReturn(question(1L, QuestionStatus.PENDING_REVIEW.code(), 1));
        BizException ex = assertThrows(BizException.class,
                () -> service(questionMapper, validNodeApi(), validCertApi()).delete(1L));
        assertEquals(QuestionErrorCode.QUESTION_DELETE_PENDING_REVIEW.code(), ex.getCode());
    }

    @Test
    void delete_offline_success() {
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        when(questionMapper.selectById(1L)).thenReturn(question(1L, QuestionStatus.OFFLINE.code(), 1));
        fullService(questionMapper, mock(QuestionOptionMapper.class),
                mock(QuestionKnowledgeNodeMapper.class), validNodeApi(), validCertApi()).delete(1L);
        verify(questionMapper).deleteById(1L);
    }

    @Test
    void restore_recycled_backToDraft() {
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        Question recycled = question(1L, QuestionStatus.RECYCLED.code(), 1);
        when(questionMapper.selectById(1L)).thenReturn(recycled);
        service(questionMapper, validNodeApi(), validCertApi()).restore(1L);
        assertEquals(QuestionStatus.DRAFT.code(), recycled.getStatus());
        verify(questionMapper).updateById(recycled);
    }

    @Test
    void restore_draft_30320() {
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        when(questionMapper.selectById(1L)).thenReturn(question(1L, QuestionStatus.DRAFT.code(), 1));
        BizException ex = assertThrows(BizException.class,
                () -> service(questionMapper, validNodeApi(), validCertApi()).restore(1L));
        assertEquals(QuestionErrorCode.QUESTION_NOT_RECYCLED.code(), ex.getCode());
    }

    @Test
    void withdrawReview_pending_backToDraft() {
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        Question pending = question(1L, QuestionStatus.PENDING_REVIEW.code(), 1);
        when(questionMapper.selectById(1L)).thenReturn(pending);
        service(questionMapper, validNodeApi(), validCertApi()).withdrawReview(1L);
        assertEquals(QuestionStatus.DRAFT.code(), pending.getStatus());
        verify(questionMapper).updateById(pending);
    }

    @Test
    void withdrawReview_draft_30306() {
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        when(questionMapper.selectById(1L)).thenReturn(question(1L, QuestionStatus.DRAFT.code(), 1));
        BizException ex = assertThrows(BizException.class,
                () -> service(questionMapper, validNodeApi(), validCertApi()).withdrawReview(1L));
        assertEquals(QuestionErrorCode.QUESTION_STATUS_INVALID_ACTION.code(), ex.getCode());
    }

    @Test
    void withdrawReview_published_30306() {
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        when(questionMapper.selectById(1L)).thenReturn(question(1L, QuestionStatus.PUBLISHED.code(), 1));
        BizException ex = assertThrows(BizException.class,
                () -> service(questionMapper, validNodeApi(), validCertApi()).withdrawReview(1L));
        assertEquals(QuestionErrorCode.QUESTION_STATUS_INVALID_ACTION.code(), ex.getCode());
    }
}
