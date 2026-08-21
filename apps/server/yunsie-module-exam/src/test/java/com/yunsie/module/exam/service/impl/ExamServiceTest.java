package com.yunsie.module.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.exam.dto.ExamCreateReq;
import com.yunsie.module.exam.dto.ExamUpdateReq;
import com.yunsie.module.exam.entity.Exam;
import com.yunsie.module.exam.entity.ExamPaper;
import com.yunsie.module.exam.enums.ExamStatus;
import com.yunsie.module.exam.enums.PaperStatus;
import com.yunsie.module.exam.error.ExamErrorCode;
import com.yunsie.module.exam.mapper.ExamAttemptMapper;
import com.yunsie.module.exam.mapper.ExamMapper;
import com.yunsie.module.exam.mapper.ExamPaperMapper;
import com.yunsie.module.exam.mapper.ExamPaperOptionMapper;
import com.yunsie.module.exam.mapper.ExamPaperQuestionMapper;
import com.yunsie.module.exam.service.PaperAssembler;
import com.yunsie.module.subject.api.SubjectQueryApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 考试服务单元测试：状态机 / 发布保护 / 删除保护。
 */
class ExamServiceTest {

    private ExamServiceImpl service(ExamMapper examMapper, ExamPaperMapper paperMapper,
                                    ExamAttemptMapper attemptMapper, PaperAssembler assembler) {
        CertificateQueryApi certApi = mock(CertificateQueryApi.class);
        when(certApi.findCertificate(100L))
                .thenReturn(new CertificateQueryApi.CertificateView(100L, 1L, "护士执业资格", "nurse", "", 1));
        return new ExamServiceImpl(examMapper, paperMapper, mock(ExamPaperQuestionMapper.class),
                mock(ExamPaperOptionMapper.class), attemptMapper, assembler,
                certApi, mock(SubjectQueryApi.class), new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private Exam exam(Long id, int status) {
        Exam exam = new Exam();
        exam.setId(id);
        exam.setCertificateId(100L);
        exam.setName("模拟考试");
        exam.setDurationMinutes(60);
        exam.setStatus(status);
        exam.setAssembleRule("{\"questionCount\":10,\"questionTypes\":[1,2,3]}");
        return exam;
    }

    private ExamCreateReq createReq() {
        return new ExamCreateReq(100L, null, null, "模拟考试", 60, null, null, null,
                new ExamCreateReq.AssembleRule(10, java.util.List.of(1, 2, 3), null, null));
    }

    private ExamCreateReq.AssembleRule rule() {
        return new ExamCreateReq.AssembleRule(10, java.util.List.of(1, 2, 3), null, null);
    }

    @Test
    void create_defaultsDraft() {
        ExamMapper examMapper = mock(ExamMapper.class);
        org.mockito.Mockito.doAnswer(inv -> {
            ((Exam) inv.getArgument(0)).setId(1L);
            return 1;
        }).when(examMapper).insert(any(Exam.class));
        Long id = service(examMapper, mock(ExamPaperMapper.class), mock(ExamAttemptMapper.class),
                mock(PaperAssembler.class)).create(createReq());
        assertEquals(1L, id);
    }

    @Test
    void update_published_blocked_30403() {
        ExamMapper examMapper = mock(ExamMapper.class);
        when(examMapper.selectById(1L)).thenReturn(exam(1L, ExamStatus.PUBLISHED.code()));
        BizException ex = assertThrows(BizException.class,
                () -> service(examMapper, mock(ExamPaperMapper.class), mock(ExamAttemptMapper.class),
                        mock(PaperAssembler.class)).update(1L, new ExamUpdateReq("改名", 60, null, null, null, rule())));
        assertEquals(ExamErrorCode.EXAM_PUBLISHED_NOT_EDITABLE.code(), ex.getCode());
    }

    @Test
    void delete_published_blocked_30405() {
        ExamMapper examMapper = mock(ExamMapper.class);
        when(examMapper.selectById(1L)).thenReturn(exam(1L, ExamStatus.PUBLISHED.code()));
        BizException ex = assertThrows(BizException.class,
                () -> service(examMapper, mock(ExamPaperMapper.class), mock(ExamAttemptMapper.class),
                        mock(PaperAssembler.class)).delete(1L));
        assertEquals(ExamErrorCode.EXAM_PUBLISHED_NOT_DELETABLE.code(), ex.getCode());
    }

    @Test
    void delete_withAttempts_blocked_30406() {
        ExamMapper examMapper = mock(ExamMapper.class);
        when(examMapper.selectById(1L)).thenReturn(exam(1L, ExamStatus.OFFLINE.code()));
        ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
        when(attemptMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        BizException ex = assertThrows(BizException.class,
                () -> service(examMapper, mock(ExamPaperMapper.class), attemptMapper,
                        mock(PaperAssembler.class)).delete(1L));
        assertEquals(ExamErrorCode.EXAM_HAS_ATTEMPTS.code(), ex.getCode());
    }

    @Test
    void assemble_published_blocked_30404() {
        ExamMapper examMapper = mock(ExamMapper.class);
        when(examMapper.selectById(1L)).thenReturn(exam(1L, ExamStatus.PUBLISHED.code()));
        BizException ex = assertThrows(BizException.class,
                () -> service(examMapper, mock(ExamPaperMapper.class), mock(ExamAttemptMapper.class),
                        mock(PaperAssembler.class)).assemble(1L));
        assertEquals(ExamErrorCode.EXAM_PUBLISHED_NOT_ASSEMBLE.code(), ex.getCode());
    }

    @Test
    void publish_withoutPaper_blocked_30407() {
        ExamMapper examMapper = mock(ExamMapper.class);
        when(examMapper.selectById(1L)).thenReturn(exam(1L, ExamStatus.DRAFT.code()));
        ExamPaperMapper paperMapper = mock(ExamPaperMapper.class);
        when(paperMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        BizException ex = assertThrows(BizException.class,
                () -> service(examMapper, paperMapper, mock(ExamAttemptMapper.class),
                        mock(PaperAssembler.class)).publish(1L));
        assertEquals(ExamErrorCode.PAPER_NOT_FOUND.code(), ex.getCode());
    }

    @Test
    void publish_withValidPaper_ok() {
        ExamMapper examMapper = mock(ExamMapper.class);
        Exam draft = exam(1L, ExamStatus.DRAFT.code());
        when(examMapper.selectById(1L)).thenReturn(draft);
        ExamPaperMapper paperMapper = mock(ExamPaperMapper.class);
        ExamPaper paper = new ExamPaper();
        paper.setId(5L);
        paper.setStatus(PaperStatus.VALID.code());
        when(paperMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(paper);

        service(examMapper, paperMapper, mock(ExamAttemptMapper.class), mock(PaperAssembler.class)).publish(1L);

        org.mockito.ArgumentCaptor<Exam> captor = org.mockito.ArgumentCaptor.forClass(Exam.class);
        org.mockito.Mockito.verify(examMapper).updateById(captor.capture());
        assertEquals(ExamStatus.PUBLISHED.code(), captor.getValue().getStatus());
    }
}
