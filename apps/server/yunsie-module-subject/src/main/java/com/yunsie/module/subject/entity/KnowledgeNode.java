package com.yunsie.module.subject.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识节点表（subject_knowledge_node）：通用模型承载 章节/知识点/子知识点。
 * 层级约定：章节 level=1（parent=0）→ 知识点 level=2 → 子知识点 level=3。
 * 树结构：parent_id + path(物化路径) + level + sort + enabled。
 */
@Getter
@Setter
@TableName("subject_knowledge_node")
public class KnowledgeNode extends BaseEntity {

    /** 所属版本ID */
    private Long versionId;

    /** 所属证书ID(冗余, 便于范围过滤) */
    private Long certificateId;

    /** 所属考试科目ID */
    private Long subjectId;

    /** 父节点ID(0=根) */
    private Long parentId;

    /** 节点类型: 1-章节 2-知识点 3-子知识点 */
    private Integer nodeType;

    /** 节点名称 */
    private String name;

    /** 节点描述（学习章节/知识点说明，Stage 2.3A） */
    private String description;

    /** 节点编码 */
    private String code;

    /** 物化路径, 如 /1/2/3/ */
    private String path;

    /** 层级(章节=1, 知识点=2, 子知识点=3) */
    private Integer level;

    /** 排序(越小越靠前) */
    private Integer sort;

    /** 启用: 1-是 0-否 */
    private Integer enabled;

    /** 来源: 1-官方大纲 2-平台自建 */
    private Integer source;

    /** 备注 */
    private String remark;
}
