package com.yunsie.common.api;

/**
 * 内容引用探针（Stage 2.3A）：供 subject 域删除守卫查询跨域引用计数。
 * 实现位于 boot（装配点），经各域 QueryApi 访问其自有表——subject 域不反向依赖
 * course/exam/question 模块（避免 Maven 环，模块边界铁律保持不变）。
 */
public interface SubjectUsageProbe {

    /** 归属该科目的课程数（course_course.subject_id） */
    long countCourseRefsBySubject(Long subjectId);

    /** 归属该科目的考试数（exam_exam.subject_id） */
    long countExamRefsBySubject(Long subjectId);

    /** 归属该章节的课程数（course_course.chapter_id） */
    long countCourseRefsByChapter(Long chapterNodeId);

    /** 关联该知识节点的题目数（question_knowledge_node.node_id） */
    long countQuestionRefsByNode(Long nodeId);
}
