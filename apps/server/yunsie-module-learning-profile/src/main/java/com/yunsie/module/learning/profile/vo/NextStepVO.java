package com.yunsie.module.learning.profile.vo;

import com.yunsie.module.question.api.QuestionQueryApi;

import java.util.List;

/**
 * 学习下一步规则推荐（Stage 2.3B；无真实 LLM，纯规则，禁止伪造 AI 生成内容）。
 */
public record NextStepVO(
        List<WeakKnowledgeVO> weakKnowledge,
        List<RecommendedCourseVO> recommendedCourses,
        List<QuestionQueryApi.PracticeQuestionView> recommendedPractice,
        String nextStep,
        /** 固定为 rule-based：前端据此标注「基于真实学习数据的规则推荐（非 AI 生成）」 */
        String source) {

    public record WeakKnowledgeVO(Long nodeId, String name, Integer masteryValue, String reason) {
    }

    public record RecommendedCourseVO(Long courseId, String title, String description) {
    }
}
