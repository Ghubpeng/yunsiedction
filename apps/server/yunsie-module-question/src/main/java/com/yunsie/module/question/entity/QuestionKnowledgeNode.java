package com.yunsie.module.question.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 题目-知识节点关联表（question_knowledge_node）。
 * 仅关联 知识点/子知识点（node_type 2/3）；节点数据归 subject 域，本表只存 node_id（无物理外键）。
 */
@Getter
@Setter
@TableName("question_knowledge_node")
public class QuestionKnowledgeNode extends BaseEntity {

    /** 题目ID */
    private Long questionId;

    /** 知识节点ID(subject域) */
    private Long nodeId;
}
