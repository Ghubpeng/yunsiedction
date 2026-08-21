package com.yunsie.module.subject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.subject.api.SubjectCommandApi;
import com.yunsie.module.subject.entity.SubjectVersion;
import com.yunsie.module.subject.mapper.ExamSubjectMapper;
import com.yunsie.module.subject.mapper.KnowledgeNodeMapper;
import com.yunsie.module.subject.mapper.SubjectVersionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 知识体系复制单元测试（Stage 2.3B）：
 * 无当前版本时回退最新版本；源证书无任何版本 → 零结果。
 */
class SubjectCommandApiImplTest {

    private SubjectCommandApiImpl api(SubjectVersionMapper versionMapper, ExamSubjectMapper subjectMapper,
                                      KnowledgeNodeMapper nodeMapper) {
        return new SubjectCommandApiImpl(subjectMapper, nodeMapper, versionMapper);
    }

    private SubjectVersion version(Long id, Integer status) {
        SubjectVersion v = new SubjectVersion();
        v.setId(id);
        v.setCertificateId(100L);
        v.setVersionNo("2026");
        v.setName("2026版");
        v.setStatus(status);
        v.setEnabled(1);
        return v;
    }

    @Test
    void copySystem_noCurrentVersion_fallsBackToLatest() {
        SubjectVersionMapper versionMapper = mock(SubjectVersionMapper.class);
        when(versionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(versionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(version(9L, 1)));
        ExamSubjectMapper subjectMapper = mock(ExamSubjectMapper.class);
        when(subjectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        KnowledgeNodeMapper nodeMapper = mock(KnowledgeNodeMapper.class);
        when(nodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        doAnswer(inv -> {
            ((SubjectVersion) inv.getArgument(0)).setId(10L);
            return 1;
        }).when(versionMapper).insert(any(SubjectVersion.class));

        SubjectCommandApi.CopyResult result = api(versionMapper, subjectMapper, nodeMapper)
                .copySystem(100L, 200L);

        assertNotNull(result.newVersionId());
        assertEquals(0, result.subjectCount());
        assertEquals(0, result.nodeCount());
    }

    @Test
    void copySystem_noVersionAtAll_emptyResult() {
        SubjectVersionMapper versionMapper = mock(SubjectVersionMapper.class);
        when(versionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(versionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        SubjectCommandApi.CopyResult result = api(versionMapper, mock(ExamSubjectMapper.class),
                mock(KnowledgeNodeMapper.class)).copySystem(100L, 200L);

        assertNull(result.newVersionId());
        assertEquals(0, result.subjectCount());
    }
}
