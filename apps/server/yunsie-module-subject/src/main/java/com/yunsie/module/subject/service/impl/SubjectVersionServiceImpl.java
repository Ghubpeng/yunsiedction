package com.yunsie.module.subject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.subject.dto.VersionCreateReq;
import com.yunsie.module.subject.dto.VersionUpdateReq;
import com.yunsie.module.subject.entity.KnowledgeNode;
import com.yunsie.module.subject.entity.SubjectVersion;
import com.yunsie.module.subject.enums.VersionStatus;
import com.yunsie.module.subject.error.SubjectErrorCode;
import com.yunsie.module.subject.mapper.KnowledgeNodeMapper;
import com.yunsie.module.subject.mapper.SubjectVersionMapper;
import com.yunsie.module.subject.service.SubjectVersionService;
import com.yunsie.module.subject.vo.VersionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 知识体系版本实现。
 */
@Service
@RequiredArgsConstructor
public class SubjectVersionServiceImpl implements SubjectVersionService {

    private final SubjectVersionMapper versionMapper;
    private final KnowledgeNodeMapper nodeMapper;
    private final CertificateQueryApi certificateQueryApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(VersionCreateReq req) {
        requireCertificate(req.certificateId());
        long count = versionMapper.selectCount(new LambdaQueryWrapper<SubjectVersion>()
                .eq(SubjectVersion::getCertificateId, req.certificateId())
                .eq(SubjectVersion::getVersionNo, req.versionNo()));
        if (count > 0) {
            throw new BizException(SubjectErrorCode.VERSION_NO_EXISTS);
        }
        SubjectVersion version = new SubjectVersion();
        version.setCertificateId(req.certificateId());
        version.setVersionNo(req.versionNo());
        version.setName(req.name());
        version.setStatus(VersionStatus.DRAFT.code());
        version.setEnabled(1);
        version.setRemark(req.remark() == null ? "" : req.remark());
        versionMapper.insert(version);
        return version.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, VersionUpdateReq req) {
        SubjectVersion version = requireVersion(id);
        if (version.getStatus() != null && version.getStatus() == VersionStatus.ARCHIVED.code()) {
            throw new BizException(SubjectErrorCode.VERSION_ARCHIVED);
        }
        version.setName(req.name());
        version.setRemark(req.remark());
        version.setEnabled(req.enabled());
        versionMapper.updateById(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCurrent(Long id) {
        SubjectVersion version = requireVersion(id);
        // 原当前版本归档
        List<SubjectVersion> currentOnes = versionMapper.selectList(new LambdaQueryWrapper<SubjectVersion>()
                .eq(SubjectVersion::getCertificateId, version.getCertificateId())
                .eq(SubjectVersion::getStatus, VersionStatus.CURRENT.code()));
        for (SubjectVersion current : currentOnes) {
            current.setStatus(VersionStatus.ARCHIVED.code());
            versionMapper.updateById(current);
        }
        version.setStatus(VersionStatus.CURRENT.code());
        version.setEnabled(1);
        versionMapper.updateById(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        SubjectVersion version = requireVersion(id);
        version.setStatus(VersionStatus.ARCHIVED.code());
        versionMapper.updateById(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SubjectVersion version = requireVersion(id);
        long nodes = nodeMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeNode>().eq(KnowledgeNode::getVersionId, id));
        if (nodes > 0) {
            throw new BizException(SubjectErrorCode.VERSION_HAS_NODES);
        }
        // 逻辑删除时释放自然键（uk(certificate_id, version_no, deleted)）
        version.setVersionNo(version.getVersionNo() + "#d" + version.getId());
        versionMapper.updateById(version);
        versionMapper.deleteById(id);
    }

    @Override
    public VersionVO get(Long id) {
        return toVO(requireVersion(id));
    }

    @Override
    public List<VersionVO> listByCertificate(Long certificateId) {
        return versionMapper.selectList(new LambdaQueryWrapper<SubjectVersion>()
                        .eq(SubjectVersion::getCertificateId, certificateId)
                        .orderByDesc(SubjectVersion::getStatus).orderByDesc(SubjectVersion::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    public VersionVO currentOfCertificate(Long certificateId) {
        SubjectVersion current = versionMapper.selectOne(new LambdaQueryWrapper<SubjectVersion>()
                .eq(SubjectVersion::getCertificateId, certificateId)
                .eq(SubjectVersion::getStatus, VersionStatus.CURRENT.code())
                .last("LIMIT 1"));
        return current == null ? null : toVO(current);
    }

    private void requireCertificate(Long certificateId) {
        if (certificateQueryApi.findCertificate(certificateId) == null) {
            throw new BizException(com.yunsie.module.certificate.error.CertErrorCode.CERT_NOT_FOUND);
        }
    }

    private SubjectVersion requireVersion(Long id) {
        SubjectVersion version = versionMapper.selectById(id);
        if (version == null) {
            throw new BizException(SubjectErrorCode.VERSION_NOT_FOUND);
        }
        return version;
    }

    private VersionVO toVO(SubjectVersion v) {
        return new VersionVO(v.getId(), v.getCertificateId(), v.getVersionNo(), v.getName(),
                v.getStatus(), v.getEnabled(), v.getRemark(), v.getCreateTime());
    }
}
