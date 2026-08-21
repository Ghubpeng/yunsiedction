package com.yunsie.module.subject.service;

import com.yunsie.module.subject.dto.NodeCreateReq;
import com.yunsie.module.subject.dto.NodeMoveReq;
import com.yunsie.module.subject.dto.NodeUpdateReq;
import com.yunsie.module.subject.vo.NodeVO;
import com.yunsie.module.subject.vo.SubjectTreeVO;

import java.util.List;

/**
 * 知识节点服务（通用模型：章节/知识点/子知识点）。
 * 层级规则：章节(parent=0) → 知识点(挂章节) → 子知识点(挂知识点)。
 */
public interface KnowledgeNodeService {

    Long create(NodeCreateReq req);

    void update(Long id, NodeUpdateReq req);

    /** 删除保护：存在子节点时禁止删除 */
    void delete(Long id);

    /** 移动：禁止移动到自身/后代/非法层级/跨版本；path 与 level 全子树级联更新 */
    void move(Long id, NodeMoveReq req);

    NodeVO get(Long id);

    List<NodeVO> children(Long id);

    /** 完整节点树（按版本，含禁用节点，后台管理用） */
    List<NodeVO> tree(Long versionId);

    /** 科目视角完整树（考试科目 → 章节 → 知识点 → 子知识点） */
    List<SubjectTreeVO> subjectTree(Long versionId);

    /** 用户公开端：当前版本 + 仅启用节点（禁用子树自动隐藏） */
    List<SubjectTreeVO> publicTree(Long certificateId);
}
