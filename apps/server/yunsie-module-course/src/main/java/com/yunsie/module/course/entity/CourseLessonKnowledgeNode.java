package com.yunsie.module.course.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 小节-知识节点关联表（course_lesson_knowledge_node）。
 * 可选关联；节点数据归 subject 域（经 SubjectQueryApi 校验：知识点/子知识点、启用、同证书）。
 */
@Getter
@Setter
@TableName("course_lesson_knowledge_node")
public class CourseLessonKnowledgeNode extends BaseEntity {

    /** 小节ID */
    private Long lessonId;

    /** 知识节点ID(subject域) */
    private Long nodeId;
}
