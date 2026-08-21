package com.yunsie.module.subject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.subject.dto.VersionCreateReq;
import com.yunsie.module.subject.entity.SubjectVersion;
import com.yunsie.module.subject.enums.VersionStatus;
import com.yunsie.module.subject.error.SubjectErrorCode;
import com.yunsie.module.subject.mapper.KnowledgeNodeMapper;
import com.yunsie.module.subject.mapper.SubjectVersionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识体系版本服务单元测试：版本号唯一 / 设为当前归档旧版本。
 */
class SubjectVersionServiceTest {

    private SubjectVersionServiceImpl service(SubjectVersionMapper versionMapper,
                                              KnowledgeNodeMapper nodeMapper,
                                              CertificateQueryApi certificateQueryApi) {
        return new SubjectVersionServiceImpl(versionMapper, nodeMapper, certificateQueryApi);
    }

    private SubjectVersion version(Long id, int status) {
        SubjectVersion v = new SubjectVersion();
        v.setId(id);
        v.setCertificateId(100L);
        v.setVersionNo("2026");
        v.setStatus(status);
        return v;
    }

    @Test
    void create_duplicateVersionNo_throws() {
        SubjectVersionMapper versionMapper = mock(SubjectVersionMapper.class);
        when(versionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        CertificateQueryApi certApi = mock(CertificateQueryApi.class);
        when(certApi.findCertificate(100L))
                .thenReturn(new CertificateQueryApi.CertificateView(100L, 1L, "护士执业资格", "nurse", "", 1));

        BizException ex = assertThrows(BizException.class,
                () -> service(versionMapper, mock(KnowledgeNodeMapper.class), certApi)
                        .create(new VersionCreateReq(100L, "2026", "2026版", "")));
        assertEquals(SubjectErrorCode.VERSION_NO_EXISTS.code(), ex.getCode());
    }

    @Test
    void setCurrent_archivesPreviousCurrent() {
        SubjectVersionMapper versionMapper = mock(SubjectVersionMapper.class);
        SubjectVersion target = version(2L, VersionStatus.DRAFT.code());
        SubjectVersion previousCurrent = version(1L, VersionStatus.CURRENT.code());
        when(versionMapper.selectById(2L)).thenReturn(target);
        when(versionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(previousCurrent));

        service(versionMapper, mock(KnowledgeNodeMapper.class), mock(CertificateQueryApi.class))
                .setCurrent(2L);

        verify(versionMapper).updateById(argThat((SubjectVersion v) -> v.getStatus() == VersionStatus.ARCHIVED.code()));
        verify(versionMapper).updateById(argThat((SubjectVersion v) -> v.getStatus() == VersionStatus.CURRENT.code()));
        assertEquals(VersionStatus.CURRENT.code(), target.getStatus());
    }
}
