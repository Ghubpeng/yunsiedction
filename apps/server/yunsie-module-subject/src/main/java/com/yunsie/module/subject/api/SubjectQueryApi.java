package com.yunsie.module.subject.api;

import java.util.List;

/**
 * 知识体系查询契约（subject 域对外 API）。
 * 供 course/question/exam/ai/learning-profile 等域使用；
 * 其他域禁止直连 subject 域 Entity/Mapper/表（模块边界铁律）。
 * 注意：掌握度后续绑定 nodeId + versionId，本契约提供节点视图以便引用。
 */
public interface SubjectQueryApi {

    SubjectView findSubject(Long subjectId);

    KnowledgeNodeView findNode(Long nodeId);

    /** 证书的当前知识体系版本 ID（无当前版本返回 null） */
    Long findCurrentVersionId(Long certificateId);

    /** 版本视图（null=不存在或已删除；Stage 2.3B 内容复制/看板） */
    VersionView findVersion(Long versionId);

    /** 证书当前版本下全部章节节点（node_type=1；无当前版本返回空；Stage 2.3B 运营看板） */
    List<KnowledgeNodeView> listCurrentVersionChapterNodes(Long certificateId);

    /** 节点的全部后代节点 ID（含子知识点；不含自身） */
    List<Long> findChildNodeIds(Long nodeId);

    /** 按编码查询证书当前版本下的知识节点（Excel 导入解析节点编码用；未匹配的编码不在结果中） */
    List<KnowledgeNodeView> findNodesByCodes(Long certificateId, List<String> codes);

    record SubjectView(Long id, Long certificateId, String name, String code, Integer enabled) {
    }

    record VersionView(Long id, Long certificateId, String versionNo, String name, Integer status, Integer enabled) {
    }

    record KnowledgeNodeView(Long id, Long versionId, Long certificateId, Long subjectId,
                             Integer nodeType, String name, String code, String path,
                             Integer level, Integer enabled) {
    }
}
