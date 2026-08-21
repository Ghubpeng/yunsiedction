package com.yunsie.module.subject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.api.SubjectUsageProbe;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.certificate.error.CertErrorCode;
import com.yunsie.module.subject.dto.NodeCreateReq;
import com.yunsie.module.subject.dto.NodeMoveReq;
import com.yunsie.module.subject.dto.NodeUpdateReq;
import com.yunsie.module.subject.entity.ExamSubject;
import com.yunsie.module.subject.entity.KnowledgeNode;
import com.yunsie.module.subject.entity.SubjectVersion;
import com.yunsie.module.subject.enums.NodeType;
import com.yunsie.module.subject.error.SubjectErrorCode;
import com.yunsie.module.subject.mapper.ExamSubjectMapper;
import com.yunsie.module.subject.mapper.KnowledgeNodeMapper;
import com.yunsie.module.subject.mapper.SubjectVersionMapper;
import com.yunsie.module.subject.service.KnowledgeNodeService;
import com.yunsie.module.subject.vo.NodeVO;
import com.yunsie.module.subject.vo.SubjectTreeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识节点实现。
 * 层级规则强制（MVP 合理默认值）：章节 parent=0；知识点父=章节；子知识点父=知识点。
 * 移动：同版本内移动；禁止自身/后代/跨版本/非法层级；path+level 全子树级联。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeNodeServiceImpl implements KnowledgeNodeService {

    private final KnowledgeNodeMapper nodeMapper;
    private final SubjectVersionMapper versionMapper;
    private final ExamSubjectMapper subjectMapper;
    private final CertificateQueryApi certificateQueryApi;
    private final SubjectUsageProbe usageProbe;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(NodeCreateReq req) {
        SubjectVersion version = requireVersion(req.versionId());
        ExamSubject subject = requireSubject(req.subjectId());
        if (!subject.getCertificateId().equals(version.getCertificateId())) {
            throw new BizException(SubjectErrorCode.NODE_PARENT_TYPE_INVALID);
        }
        long count = nodeMapper.selectCount(new LambdaQueryWrapper<KnowledgeNode>()
                .eq(KnowledgeNode::getVersionId, req.versionId())
                .eq(KnowledgeNode::getCode, req.code()));
        if (count > 0) {
            throw new BizException(SubjectErrorCode.NODE_CODE_EXISTS);
        }
        NodeType nodeType = NodeType.of(req.nodeType());
        KnowledgeNode parent = null;
        if (req.parentId() != null && req.parentId() != 0) {
            parent = requireNode(req.parentId());
            validateParentType(nodeType, parent);
            if (!parent.getVersionId().equals(version.getId()) || !parent.getSubjectId().equals(subject.getId())) {
                throw new BizException(SubjectErrorCode.NODE_PARENT_TYPE_INVALID);
            }
        } else {
            if (nodeType != NodeType.CHAPTER) {
                throw new BizException(SubjectErrorCode.NODE_PARENT_TYPE_INVALID);
            }
        }
        KnowledgeNode node = new KnowledgeNode();
        node.setVersionId(version.getId());
        node.setCertificateId(version.getCertificateId());
        node.setSubjectId(subject.getId());
        node.setParentId(parent == null ? 0L : parent.getId());
        node.setNodeType(nodeType.code());
        node.setName(req.name());
        node.setDescription(req.description() == null ? "" : req.description());
        node.setCode(req.code());
        node.setSort(req.sort() == null ? 0 : req.sort());
        node.setEnabled(req.enabled() == null ? 1 : req.enabled());
        node.setSource(req.source() == null ? 1 : req.source());
        node.setRemark(req.remark() == null ? "" : req.remark());
        node.setLevel(parent == null ? 1 : parent.getLevel() + 1);
        nodeMapper.insert(node);
        node.setPath((parent == null ? "/" : parent.getPath()) + node.getId() + "/");
        nodeMapper.updateById(node);
        return node.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, NodeUpdateReq req) {
        KnowledgeNode node = requireNode(id);
        node.setName(req.name());
        node.setDescription(req.description());
        node.setSort(req.sort());
        node.setEnabled(req.enabled());
        node.setSource(req.source());
        node.setRemark(req.remark());
        nodeMapper.updateById(node);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        KnowledgeNode node = requireNode(id);
        long children = nodeMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeNode>().eq(KnowledgeNode::getParentId, id));
        if (children > 0) {
            throw new BizException(SubjectErrorCode.NODE_HAS_CHILDREN);
        }
        // Stage 2.3A 删除保护：章节被课程归属 / 知识点被题目关联 → 禁止删除（提示停用）
        if (node.getNodeType() != null && node.getNodeType() == NodeType.CHAPTER.code()
                && usageProbe.countCourseRefsByChapter(id) > 0) {
            throw new BizException(SubjectErrorCode.NODE_HAS_COURSES);
        }
        if (usageProbe.countQuestionRefsByNode(id) > 0) {
            throw new BizException(SubjectErrorCode.NODE_HAS_QUESTIONS);
        }
        // 逻辑删除时释放自然键（uk(version_id, code, deleted)）
        node.setCode(node.getCode() + "#d" + node.getId());
        nodeMapper.updateById(node);
        nodeMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(Long id, NodeMoveReq req) {
        KnowledgeNode node = requireNode(id);
        String oldPath = node.getPath();
        int oldLevel = node.getLevel();

        String newParentPath;
        int newLevel;
        if (req.newParentId() == null || req.newParentId() == 0) {
            if (NodeType.of(node.getNodeType()) != NodeType.CHAPTER) {
                throw new BizException(SubjectErrorCode.NODE_MOVE_INVALID);
            }
            newParentPath = "/";
            newLevel = 1;
        } else {
            KnowledgeNode newParent = requireNode(req.newParentId());
            if (newParent.getId().equals(node.getId()) || newParent.getPath().startsWith(node.getPath())) {
                throw new BizException(SubjectErrorCode.NODE_MOVE_INVALID);
            }
            if (!newParent.getVersionId().equals(node.getVersionId())) {
                throw new BizException(SubjectErrorCode.NODE_MOVE_INVALID);
            }
            validateParentType(NodeType.of(node.getNodeType()), newParent);
            newParentPath = newParent.getPath();
            newLevel = newParent.getLevel() + 1;
        }

        String newPath = newParentPath + node.getId() + "/";
        int levelDelta = newLevel - oldLevel;

        node.setParentId(req.newParentId() == null ? 0L : req.newParentId());
        node.setPath(newPath);
        node.setLevel(newLevel);
        if (req.sort() != null) {
            node.setSort(req.sort());
        }
        nodeMapper.updateById(node);

        List<KnowledgeNode> descendants = nodeMapper.selectList(new LambdaQueryWrapper<KnowledgeNode>()
                .likeRight(KnowledgeNode::getPath, oldPath));
        for (KnowledgeNode d : descendants) {
            if (d.getId().equals(node.getId())) {
                continue;
            }
            d.setPath(newPath + d.getPath().substring(oldPath.length()));
            d.setLevel(d.getLevel() + levelDelta);
            nodeMapper.updateById(d);
        }
    }

    @Override
    public NodeVO get(Long id) {
        return toVO(requireNode(id), List.of());
    }

    @Override
    public List<NodeVO> children(Long id) {
        requireNode(id);
        return nodeMapper.selectList(new LambdaQueryWrapper<KnowledgeNode>()
                        .eq(KnowledgeNode::getParentId, id)
                        .orderByAsc(KnowledgeNode::getSort).orderByAsc(KnowledgeNode::getId))
                .stream().map(n -> toVO(n, List.of())).toList();
    }

    @Override
    public List<NodeVO> tree(Long versionId) {
        requireVersion(versionId);
        List<KnowledgeNode> all = listByVersion(versionId);
        return build(all, 0L, false);
    }

    @Override
    public List<SubjectTreeVO> subjectTree(Long versionId) {
        SubjectVersion version = requireVersion(versionId);
        Map<Long, List<KnowledgeNode>> bySubject = listByVersion(versionId).stream()
                .collect(Collectors.groupingBy(KnowledgeNode::getSubjectId));
        List<ExamSubject> subjects = subjectMapper.selectList(new LambdaQueryWrapper<ExamSubject>()
                .eq(ExamSubject::getCertificateId, version.getCertificateId())
                .orderByAsc(ExamSubject::getSort).orderByAsc(ExamSubject::getId));
        return subjects.stream()
                .map(s -> {
                    List<KnowledgeNode> nodes = bySubject.getOrDefault(s.getId(), List.of());
                    Map<Long, List<KnowledgeNode>> byParent = nodes.stream()
                            .collect(Collectors.groupingBy(KnowledgeNode::getParentId));
                    return new SubjectTreeVO(s.getId(), s.getName(), buildByMap(0L, byParent, false));
                })
                .toList();
    }

    @Override
    public List<SubjectTreeVO> publicTree(Long certificateId) {
        if (!certificateQueryApi.existsEnabled(certificateId)) {
            throw new BizException(CertErrorCode.CERT_NOT_FOUND);
        }
        SubjectVersion current = versionMapper.selectOne(new LambdaQueryWrapper<SubjectVersion>()
                .eq(SubjectVersion::getCertificateId, certificateId)
                .eq(SubjectVersion::getStatus, 2)
                .last("LIMIT 1"));
        if (current == null) {
            throw new BizException(SubjectErrorCode.CURRENT_VERSION_NOT_FOUND);
        }
        Map<Long, List<KnowledgeNode>> bySubject = listByVersion(current.getId()).stream()
                .filter(n -> n.getEnabled() != null && n.getEnabled() == 1)
                .collect(Collectors.groupingBy(KnowledgeNode::getSubjectId));
        List<ExamSubject> subjects = subjectMapper.selectList(new LambdaQueryWrapper<ExamSubject>()
                .eq(ExamSubject::getCertificateId, certificateId)
                .eq(ExamSubject::getEnabled, 1)
                .orderByAsc(ExamSubject::getSort).orderByAsc(ExamSubject::getId));
        return subjects.stream()
                .map(s -> {
                    List<KnowledgeNode> nodes = bySubject.getOrDefault(s.getId(), List.of());
                    Map<Long, List<KnowledgeNode>> byParent = nodes.stream()
                            .collect(Collectors.groupingBy(KnowledgeNode::getParentId));
                    return new SubjectTreeVO(s.getId(), s.getName(), buildByMap(0L, byParent, true));
                })
                .toList();
    }

    private void validateParentType(NodeType nodeType, KnowledgeNode parent) {
        boolean valid = switch (nodeType) {
            case KNOWLEDGE_POINT -> NodeType.of(parent.getNodeType()) == NodeType.CHAPTER;
            case CHILD_KNOWLEDGE_POINT -> NodeType.of(parent.getNodeType()) == NodeType.KNOWLEDGE_POINT;
            default -> false;
        };
        if (!valid) {
            throw new BizException(SubjectErrorCode.NODE_PARENT_TYPE_INVALID);
        }
    }

    private List<KnowledgeNode> listByVersion(Long versionId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<KnowledgeNode>()
                .eq(KnowledgeNode::getVersionId, versionId)
                .orderByAsc(KnowledgeNode::getSort).orderByAsc(KnowledgeNode::getId));
    }

    private List<NodeVO> build(List<KnowledgeNode> all, Long parentId, boolean enabledOnly) {
        List<KnowledgeNode> visible = enabledOnly
                ? all.stream().filter(n -> n.getEnabled() != null && n.getEnabled() == 1).toList()
                : all;
        Map<Long, List<KnowledgeNode>> byParent = visible.stream()
                .collect(Collectors.groupingBy(KnowledgeNode::getParentId));
        return buildByMap(parentId, byParent, enabledOnly);
    }

    private List<NodeVO> buildByMap(Long parentId, Map<Long, List<KnowledgeNode>> byParent, boolean enabledOnly) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(n -> toVO(n, buildByMap(n.getId(), byParent, enabledOnly)))
                .toList();
    }

    private NodeVO toVO(KnowledgeNode n, List<NodeVO> children) {
        return new NodeVO(n.getId(), n.getParentId(), n.getNodeType(), n.getName(), n.getDescription(),
                n.getCode(), n.getPath(), n.getLevel(), n.getSort(), n.getEnabled(), n.getSource(), children);
    }

    private SubjectVersion requireVersion(Long versionId) {
        SubjectVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BizException(SubjectErrorCode.VERSION_NOT_FOUND);
        }
        return version;
    }

    private ExamSubject requireSubject(Long subjectId) {
        ExamSubject subject = subjectMapper.selectById(subjectId);
        if (subject == null) {
            throw new BizException(SubjectErrorCode.SUBJECT_NOT_FOUND);
        }
        return subject;
    }

    private KnowledgeNode requireNode(Long nodeId) {
        KnowledgeNode node = nodeMapper.selectById(nodeId);
        if (node == null) {
            throw new BizException(SubjectErrorCode.NODE_NOT_FOUND);
        }
        return node;
    }
}
