package com.yunsie.module.subject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.subject.api.SubjectQueryApi;
import com.yunsie.module.subject.entity.ExamSubject;
import com.yunsie.module.subject.entity.KnowledgeNode;
import com.yunsie.module.subject.entity.SubjectVersion;
import com.yunsie.module.subject.enums.NodeType;
import com.yunsie.module.subject.enums.VersionStatus;
import com.yunsie.module.subject.mapper.ExamSubjectMapper;
import com.yunsie.module.subject.mapper.KnowledgeNodeMapper;
import com.yunsie.module.subject.mapper.SubjectVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识体系查询契约实现。
 */
@Service
@RequiredArgsConstructor
public class SubjectQueryApiImpl implements SubjectQueryApi {

    private final ExamSubjectMapper subjectMapper;
    private final KnowledgeNodeMapper nodeMapper;
    private final SubjectVersionMapper versionMapper;

    @Override
    public SubjectView findSubject(Long subjectId) {
        ExamSubject s = subjectMapper.selectById(subjectId);
        if (s == null) {
            return null;
        }
        return new SubjectView(s.getId(), s.getCertificateId(), s.getName(), s.getCode(), s.getEnabled());
    }

    @Override
    public KnowledgeNodeView findNode(Long nodeId) {
        KnowledgeNode n = nodeMapper.selectById(nodeId);
        if (n == null) {
            return null;
        }
        return new KnowledgeNodeView(n.getId(), n.getVersionId(), n.getCertificateId(), n.getSubjectId(),
                n.getNodeType(), n.getName(), n.getCode(), n.getPath(), n.getLevel(), n.getEnabled());
    }

    @Override
    public Long findCurrentVersionId(Long certificateId) {
        SubjectVersion current = versionMapper.selectOne(new LambdaQueryWrapper<SubjectVersion>()
                .eq(SubjectVersion::getCertificateId, certificateId)
                .eq(SubjectVersion::getStatus, VersionStatus.CURRENT.code())
                .last("LIMIT 1"));
        return current == null ? null : current.getId();
    }

    @Override
    public VersionView findVersion(Long versionId) {
        SubjectVersion v = versionMapper.selectById(versionId);
        if (v == null) {
            return null;
        }
        return new VersionView(v.getId(), v.getCertificateId(), v.getVersionNo(), v.getName(),
                v.getStatus(), v.getEnabled());
    }

    @Override
    public List<KnowledgeNodeView> listCurrentVersionChapterNodes(Long certificateId) {
        Long currentVersionId = findCurrentVersionId(certificateId);
        if (currentVersionId == null) {
            return List.of();
        }
        return nodeMapper.selectList(new LambdaQueryWrapper<KnowledgeNode>()
                        .eq(KnowledgeNode::getVersionId, currentVersionId)
                        .eq(KnowledgeNode::getNodeType, NodeType.CHAPTER.code())
                        .orderByAsc(KnowledgeNode::getSort).orderByAsc(KnowledgeNode::getId))
                .stream()
                .map(n -> new KnowledgeNodeView(n.getId(), n.getVersionId(), n.getCertificateId(), n.getSubjectId(),
                        n.getNodeType(), n.getName(), n.getCode(), n.getPath(), n.getLevel(), n.getEnabled()))
                .toList();
    }

    @Override
    public List<Long> findChildNodeIds(Long nodeId) {
        KnowledgeNode node = nodeMapper.selectById(nodeId);
        if (node == null) {
            return List.of();
        }
        return nodeMapper.selectList(new LambdaQueryWrapper<KnowledgeNode>()
                        .likeRight(KnowledgeNode::getPath, node.getPath())
                        .ne(KnowledgeNode::getId, nodeId))
                .stream().map(KnowledgeNode::getId).toList();
    }

    @Override
    public List<KnowledgeNodeView> findNodesByCodes(Long certificateId, List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Long currentVersionId = findCurrentVersionId(certificateId);
        if (currentVersionId == null) {
            return List.of();
        }
        return nodeMapper.selectList(new LambdaQueryWrapper<KnowledgeNode>()
                        .eq(KnowledgeNode::getVersionId, currentVersionId)
                        .in(KnowledgeNode::getCode, codes))
                .stream()
                .map(n -> new KnowledgeNodeView(n.getId(), n.getVersionId(), n.getCertificateId(), n.getSubjectId(),
                        n.getNodeType(), n.getName(), n.getCode(), n.getPath(), n.getLevel(), n.getEnabled()))
                .toList();
    }
}
