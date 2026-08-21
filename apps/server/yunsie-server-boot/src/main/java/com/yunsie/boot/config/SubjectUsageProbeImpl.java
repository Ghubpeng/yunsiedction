package com.yunsie.boot.config;

import com.yunsie.common.api.SubjectUsageProbe;
import com.yunsie.module.course.api.CourseQueryApi;
import com.yunsie.module.exam.api.ExamQueryApi;
import com.yunsie.module.question.api.QuestionQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 内容引用探针实现（boot 装配点，Stage 2.3A）：
 * subject 域删除守卫经本探针查询跨域引用（course/exam/question 表仍归各自域访问），
 * 保持模块依赖无环（subject 不反向依赖任何业务域）。
 */
@Component
@RequiredArgsConstructor
public class SubjectUsageProbeImpl implements SubjectUsageProbe {

    private final CourseQueryApi courseQueryApi;
    private final ExamQueryApi examQueryApi;
    private final QuestionQueryApi questionQueryApi;

    @Override
    public long countCourseRefsBySubject(Long subjectId) {
        return courseQueryApi.countBySubject(subjectId);
    }

    @Override
    public long countExamRefsBySubject(Long subjectId) {
        return examQueryApi.countBySubject(subjectId);
    }

    @Override
    public long countCourseRefsByChapter(Long chapterNodeId) {
        return courseQueryApi.countByChapter(chapterNodeId);
    }

    @Override
    public long countQuestionRefsByNode(Long nodeId) {
        return questionQueryApi.countByNode(nodeId);
    }
}
