package com.yunsie.module.subject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.api.SubjectUsageProbe;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.subject.dto.SubjectCreateReq;
import com.yunsie.module.subject.dto.SubjectUpdateReq;
import com.yunsie.module.subject.entity.ExamSubject;
import com.yunsie.module.subject.entity.KnowledgeNode;
import com.yunsie.module.subject.error.SubjectErrorCode;
import com.yunsie.module.subject.mapper.ExamSubjectMapper;
import com.yunsie.module.subject.mapper.KnowledgeNodeMapper;
import com.yunsie.module.subject.service.ExamSubjectService;
import com.yunsie.module.subject.vo.SubjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 考试科目实现。证书校验经 certificate 域契约 CertificateQueryApi（不直连证书表）。
 */
@Service
@RequiredArgsConstructor
public class ExamSubjectServiceImpl implements ExamSubjectService {

    private final ExamSubjectMapper subjectMapper;
    private final KnowledgeNodeMapper nodeMapper;
    private final CertificateQueryApi certificateQueryApi;
    private final SubjectUsageProbe usageProbe;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SubjectCreateReq req) {
        requireCertificate(req.certificateId());
        long count = subjectMapper.selectCount(new LambdaQueryWrapper<ExamSubject>()
                .eq(ExamSubject::getCertificateId, req.certificateId())
                .eq(ExamSubject::getCode, req.code()));
        if (count > 0) {
            throw new BizException(SubjectErrorCode.SUBJECT_CODE_EXISTS);
        }
        ExamSubject subject = new ExamSubject();
        subject.setCertificateId(req.certificateId());
        subject.setName(req.name());
        subject.setCode(req.code());
        subject.setSort(req.sort() == null ? 0 : req.sort());
        subject.setEnabled(req.enabled() == null ? 1 : req.enabled());
        subject.setSource(req.source() == null ? 1 : req.source());
        subject.setDescription(req.description() == null ? "" : req.description());
        subjectMapper.insert(subject);
        return subject.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SubjectUpdateReq req) {
        ExamSubject subject = requireSubject(id);
        subject.setName(req.name());
        subject.setSort(req.sort());
        subject.setEnabled(req.enabled());
        subject.setDescription(req.description());
        subjectMapper.updateById(subject);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ExamSubject subject = requireSubject(id);
        long nodes = nodeMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeNode>().eq(KnowledgeNode::getSubjectId, id));
        if (nodes > 0) {
            throw new BizException(SubjectErrorCode.SUBJECT_HAS_NODES);
        }
        // Stage 2.3A 删除保护：科目被课程/考试归属 → 禁止删除，提示停用
        if (usageProbe.countCourseRefsBySubject(id) > 0) {
            throw new BizException(SubjectErrorCode.SUBJECT_HAS_COURSES);
        }
        if (usageProbe.countExamRefsBySubject(id) > 0) {
            throw new BizException(SubjectErrorCode.SUBJECT_HAS_EXAMS);
        }
        // 逻辑删除时释放自然键（uk(certificate_id, code, deleted)）
        subject.setCode(subject.getCode() + "#d" + subject.getId());
        subjectMapper.updateById(subject);
        subjectMapper.deleteById(id);
    }

    @Override
    public SubjectVO get(Long id) {
        return toVO(requireSubject(id));
    }

    @Override
    public List<SubjectVO> listByCertificate(Long certificateId) {
        return subjectMapper.selectList(new LambdaQueryWrapper<ExamSubject>()
                        .eq(ExamSubject::getCertificateId, certificateId)
                        .orderByAsc(ExamSubject::getSort).orderByAsc(ExamSubject::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    public List<SubjectVO> publicListByCertificate(Long certificateId) {
        if (!certificateQueryApi.existsEnabled(certificateId)) {
            throw new BizException(com.yunsie.module.certificate.error.CertErrorCode.CERT_NOT_FOUND);
        }
        return subjectMapper.selectList(new LambdaQueryWrapper<ExamSubject>()
                        .eq(ExamSubject::getCertificateId, certificateId)
                        .eq(ExamSubject::getEnabled, 1)
                        .orderByAsc(ExamSubject::getSort).orderByAsc(ExamSubject::getId))
                .stream().map(this::toVO).toList();
    }

    private void requireCertificate(Long certificateId) {
        if (certificateQueryApi.findCertificate(certificateId) == null) {
            throw new BizException(com.yunsie.module.certificate.error.CertErrorCode.CERT_NOT_FOUND);
        }
    }

    private ExamSubject requireSubject(Long id) {
        ExamSubject subject = subjectMapper.selectById(id);
        if (subject == null) {
            throw new BizException(SubjectErrorCode.SUBJECT_NOT_FOUND);
        }
        return subject;
    }

    private SubjectVO toVO(ExamSubject s) {
        return new SubjectVO(s.getId(), s.getCertificateId(), s.getName(), s.getCode(), s.getSort(),
                s.getEnabled(), s.getSource(), s.getDescription(), s.getCreateTime());
    }
}
