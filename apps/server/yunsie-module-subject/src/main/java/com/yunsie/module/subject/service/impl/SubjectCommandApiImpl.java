package com.yunsie.module.subject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.subject.api.SubjectCommandApi;
import com.yunsie.module.subject.entity.ExamSubject;
import com.yunsie.module.subject.entity.KnowledgeNode;
import com.yunsie.module.subject.entity.SubjectVersion;
import com.yunsie.module.subject.enums.VersionStatus;
import com.yunsie.module.subject.mapper.ExamSubjectMapper;
import com.yunsie.module.subject.mapper.KnowledgeNodeMapper;
import com.yunsie.module.subject.mapper.SubjectVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识体系复制实现（Stage 2.3B）：
 * 复制源证书当前版本 → 目标证书新「当前」版本 + 全部科目 + 全部节点（层级/path/level 重算）。
 * 编码沿用源值：uk 为 (certificate_id, code) / (version_id, code)，新证书/新版本下不冲突。
 */
@Service
@RequiredArgsConstructor
public class SubjectCommandApiImpl implements SubjectCommandApi {

    private final ExamSubjectMapper subjectMapper;
    private final KnowledgeNodeMapper nodeMapper;
    private final SubjectVersionMapper versionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CopyResult copySystem(Long sourceCertId, Long targetCertId) {
        // 优先当前版本；未设当前版本时回退到最新版本（复制不因管理员未设当前版本而失败）
        SubjectVersion sourceVersion = versionMapper.selectOne(new LambdaQueryWrapper<SubjectVersion>()
                .eq(SubjectVersion::getCertificateId, sourceCertId)
                .eq(SubjectVersion::getStatus, VersionStatus.CURRENT.code())
                .last("LIMIT 1"));
        if (sourceVersion == null) {
            sourceVersion = versionMapper.selectList(new LambdaQueryWrapper<SubjectVersion>()
                            .eq(SubjectVersion::getCertificateId, sourceCertId)
                            .orderByDesc(SubjectVersion::getId)
                            .last("LIMIT 1"))
                    .stream().findFirst().orElse(null);
        }
        if (sourceVersion == null) {
            return CopyResult.empty();
        }

        SubjectVersion targetVersion = new SubjectVersion();
        targetVersion.setCertificateId(targetCertId);
        targetVersion.setVersionNo(sourceVersion.getVersionNo() + "-副本");
        targetVersion.setName(sourceVersion.getName() + "（副本）");
        targetVersion.setStatus(VersionStatus.CURRENT.code());
        targetVersion.setEnabled(sourceVersion.getEnabled());
        targetVersion.setRemark(sourceVersion.getRemark());
        versionMapper.insert(targetVersion);

        Map<Long, Long> subjectIdMap = copySubjects(sourceCertId, targetCertId);
        Map<Long, Long> nodeIdMap = copyNodes(sourceVersion.getId(), targetCertId, targetVersion.getId(), subjectIdMap);
        return new CopyResult(subjectIdMap.size(), nodeIdMap.size(), targetVersion.getId(),
                subjectIdMap, nodeIdMap);
    }

    private Map<Long, Long> copySubjects(Long sourceCertId, Long targetCertId) {
        List<ExamSubject> subjects = subjectMapper.selectList(new LambdaQueryWrapper<ExamSubject>()
                .eq(ExamSubject::getCertificateId, sourceCertId)
                .orderByAsc(ExamSubject::getSort).orderByAsc(ExamSubject::getId));
        Map<Long, Long> map = new HashMap<>();
        for (ExamSubject s : subjects) {
            ExamSubject copy = new ExamSubject();
            copy.setCertificateId(targetCertId);
            copy.setName(s.getName());
            copy.setCode(s.getCode());
            copy.setSort(s.getSort());
            copy.setEnabled(s.getEnabled());
            copy.setSource(s.getSource());
            copy.setDescription(s.getDescription());
            subjectMapper.insert(copy);
            map.put(s.getId(), copy.getId());
        }
        return map;
    }

    private Map<Long, Long> copyNodes(Long sourceVersionId, Long targetCertId, Long targetVersionId,
                                      Map<Long, Long> subjectIdMap) {
        // 按层级升序：父节点必先于子节点复制，保证 parentId/path 可映射
        List<KnowledgeNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<KnowledgeNode>()
                .eq(KnowledgeNode::getVersionId, sourceVersionId)
                .orderByAsc(KnowledgeNode::getLevel).orderByAsc(KnowledgeNode::getId));
        Map<Long, Long> map = new HashMap<>();
        Map<Long, String> newPaths = new HashMap<>(); // 新节点 id -> path（path 随父节点拼接）
        for (KnowledgeNode n : nodes) {
            Long mappedSubjectId = subjectIdMap.get(n.getSubjectId());
            if (mappedSubjectId == null) {
                continue;
            }
            Long mappedParentId = (n.getParentId() == null || n.getParentId() == 0) ? 0L : map.get(n.getParentId());
            if (mappedParentId == null) {
                continue; // 父节点未复制（异常数据）→ 跳过子树
            }
            KnowledgeNode copy = new KnowledgeNode();
            copy.setVersionId(targetVersionId);
            copy.setCertificateId(targetCertId);
            copy.setSubjectId(mappedSubjectId);
            copy.setParentId(mappedParentId);
            copy.setNodeType(n.getNodeType());
            copy.setName(n.getName());
            copy.setDescription(n.getDescription());
            copy.setCode(n.getCode());
            copy.setSort(n.getSort());
            copy.setEnabled(n.getEnabled());
            copy.setSource(n.getSource());
            copy.setRemark(n.getRemark());
            copy.setLevel(n.getLevel());
            nodeMapper.insert(copy);
            String path = mappedParentId == 0
                    ? "/" + copy.getId() + "/"
                    : newPaths.getOrDefault(mappedParentId, "/") + copy.getId() + "/";
            copy.setPath(path);
            nodeMapper.updateById(copy);
            newPaths.put(copy.getId(), path);
            map.put(n.getId(), copy.getId());
        }
        return map;
    }
}
