package com.yunsie.module.subject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.api.SubjectUsageProbe;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.subject.entity.ExamSubject;
import com.yunsie.module.subject.error.SubjectErrorCode;
import com.yunsie.module.subject.mapper.ExamSubjectMapper;
import com.yunsie.module.subject.mapper.KnowledgeNodeMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 科目服务单元测试（Stage 2.3A）：删除保护——被课程/考试引用禁止删除，提示停用。
 */
class ExamSubjectServiceTest {

    private ExamSubjectServiceImpl service(ExamSubjectMapper subjectMapper, KnowledgeNodeMapper nodeMapper,
                                           SubjectUsageProbe probe) {
        return new ExamSubjectServiceImpl(subjectMapper, nodeMapper, mock(CertificateQueryApi.class), probe);
    }

    private ExamSubject subject(Long id, String code) {
        ExamSubject s = new ExamSubject();
        s.setId(id);
        s.setCertificateId(100L);
        s.setName("科目" + id);
        s.setCode(code);
        return s;
    }

    @Test
    void delete_subjectHasCourses_30215() {
        ExamSubjectMapper subjectMapper = mock(ExamSubjectMapper.class);
        when(subjectMapper.selectById(1L)).thenReturn(subject(1L, "S1"));
        KnowledgeNodeMapper nodeMapper = mock(KnowledgeNodeMapper.class);
        when(nodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        SubjectUsageProbe probe = mock(SubjectUsageProbe.class);
        when(probe.countCourseRefsBySubject(1L)).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> service(subjectMapper, nodeMapper, probe).delete(1L));
        assertEquals(SubjectErrorCode.SUBJECT_HAS_COURSES.code(), ex.getCode());
    }

    @Test
    void delete_subjectHasExams_30216() {
        ExamSubjectMapper subjectMapper = mock(ExamSubjectMapper.class);
        when(subjectMapper.selectById(1L)).thenReturn(subject(1L, "S1"));
        KnowledgeNodeMapper nodeMapper = mock(KnowledgeNodeMapper.class);
        when(nodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        SubjectUsageProbe probe = mock(SubjectUsageProbe.class);
        when(probe.countCourseRefsBySubject(1L)).thenReturn(0L);
        when(probe.countExamRefsBySubject(1L)).thenReturn(2L);

        BizException ex = assertThrows(BizException.class,
                () -> service(subjectMapper, nodeMapper, probe).delete(1L));
        assertEquals(SubjectErrorCode.SUBJECT_HAS_EXAMS.code(), ex.getCode());
    }

    @Test
    void delete_subjectNoRefs_success() {
        ExamSubjectMapper subjectMapper = mock(ExamSubjectMapper.class);
        when(subjectMapper.selectById(1L)).thenReturn(subject(1L, "S1"));
        KnowledgeNodeMapper nodeMapper = mock(KnowledgeNodeMapper.class);
        when(nodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        SubjectUsageProbe probe = mock(SubjectUsageProbe.class);
        when(probe.countCourseRefsBySubject(1L)).thenReturn(0L);
        when(probe.countExamRefsBySubject(1L)).thenReturn(0L);

        service(subjectMapper, nodeMapper, probe).delete(1L);
        verify(subjectMapper).deleteById(1L);
    }
}
