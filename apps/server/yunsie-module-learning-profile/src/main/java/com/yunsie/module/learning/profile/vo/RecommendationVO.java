package com.yunsie.module.learning.profile.vo;

import com.yunsie.module.question.api.QuestionQueryApi;

import java.util.List;

/**
 * 针对性练习推荐视图（基于真实数据：错题节点 → 低掌握节点 → 长期未练节点；不足则诚实返回已有）。
 */
public record RecommendationVO(
        String reason,
        List<NodeItem> nodes,
        List<QuestionQueryApi.PracticeQuestionView> questions) {

    public record NodeItem(Long id, String name) {
    }
}
