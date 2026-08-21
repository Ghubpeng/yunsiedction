package com.yunsie.module.learning.profile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.course.api.CourseQueryApi;
import com.yunsie.module.learning.profile.entity.LearnMastery;
import com.yunsie.module.learning.profile.mapper.LearnMasteryMapper;
import com.yunsie.module.learning.profile.vo.NextStepVO;
import com.yunsie.module.learning.profile.vo.RecommendationVO;
import com.yunsie.module.question.api.QuestionQueryApi;
import com.yunsie.module.subject.api.SubjectQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 学习下一步规则引擎（Stage 2.3B，无真实 LLM）：
 * 基于真实掌握度/错题/课程关联数据产出 薄弱知识点 → 推荐课程 → 推荐练习 → 下一步建议。
 * 数据不足时诚实返回空列表与明确文案（禁止伪造/补齐）。
 */
@Service
@RequiredArgsConstructor
public class NextStepRuleService {

    private final ProfileSyncService syncService;
    private final LearnMasteryMapper masteryMapper;
    private final QuestionQueryApi questionQueryApi;
    private final SubjectQueryApi subjectQueryApi;
    private final CourseQueryApi courseQueryApi;
    private final PracticeRecommendationService practiceRecommendationService;

    public NextStepVO nextStep(Long userId, Long certificateId) {
        syncService.sync(userId);
        List<NextStepVO.WeakKnowledgeVO> weak = weakKnowledge(userId, certificateId);
        List<NextStepVO.RecommendedCourseVO> courses = recommendedCourses(weak);
        RecommendationVO practice = practiceRecommendationService.recommend(userId, certificateId, 5);
        return new NextStepVO(weak, courses, practice.questions(),
                buildNextStep(weak, courses, practice.questions()), "rule-based");
    }

    /** 薄弱知识点：①未解决错题节点（次数降序）②低掌握节点（当前版本 mastery 升序）；去重取前 3 */
    private List<NextStepVO.WeakKnowledgeVO> weakKnowledge(Long userId, Long certificateId) {
        List<NextStepVO.WeakKnowledgeVO> result = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();

        List<Long> mistakeNodes = questionQueryApi.listMistakeStats(userId).stream()
                .sorted(Comparator.comparing(QuestionQueryApi.MistakeStatView::mistakeCount).reversed()
                        .thenComparing(QuestionQueryApi.MistakeStatView::lastMistakeTime,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .map(QuestionQueryApi.MistakeStatView::nodeId)
                .toList();
        for (Long nodeId : mistakeNodes) {
            if (result.size() >= 3) {
                break;
            }
            SubjectQueryApi.KnowledgeNodeView node = subjectQueryApi.findNode(nodeId);
            if (node == null || (certificateId != null && !certificateId.equals(node.certificateId()))) {
                continue;
            }
            if (seen.add(nodeId)) {
                result.add(new NextStepVO.WeakKnowledgeVO(nodeId, node.name(), null, "错题"));
            }
        }

        List<LearnMastery> rows = masteryMapper.selectList(new LambdaQueryWrapper<LearnMastery>()
                .eq(LearnMastery::getUserId, userId));
        rows.stream()
                .filter(m -> {
                    Long current = subjectQueryApi.findCurrentVersionId(m.getCertificateId());
                    return current != null && current.equals(m.getVersionId());
                })
                .filter(m -> certificateId == null || certificateId.equals(m.getCertificateId()))
                .sorted(Comparator.comparing(LearnMastery::getMasteryValue,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(m -> {
                    if (result.size() >= 3) {
                        return;
                    }
                    if (seen.add(m.getNodeId())) {
                        SubjectQueryApi.KnowledgeNodeView node = subjectQueryApi.findNode(m.getNodeId());
                        result.add(new NextStepVO.WeakKnowledgeVO(m.getNodeId(),
                                node == null ? null : node.name(), m.getMasteryValue(), "低掌握"));
                    }
                });
        return result;
    }

    /** 推荐课程：覆盖薄弱知识点的已发布课程（去重，前 3） */
    private List<NextStepVO.RecommendedCourseVO> recommendedCourses(List<NextStepVO.WeakKnowledgeVO> weak) {
        List<Long> nodeIds = weak.stream().map(NextStepVO.WeakKnowledgeVO::nodeId).toList();
        if (nodeIds.isEmpty()) {
            return List.of();
        }
        return courseQueryApi.listPublishedCourseIdsByNodeIds(nodeIds).stream()
                .map(courseQueryApi::findPublishedCourse)
                .filter(Objects::nonNull)
                .limit(3)
                .map(c -> new NextStepVO.RecommendedCourseVO(c.id(), c.title(), c.description()))
                .toList();
    }

    /** 规则化建议文案（数据不足诚实说明，绝不伪造内容） */
    private String buildNextStep(List<NextStepVO.WeakKnowledgeVO> weak,
                                 List<NextStepVO.RecommendedCourseVO> courses,
                                 List<QuestionQueryApi.PracticeQuestionView> practice) {
        if (weak.isEmpty() && courses.isEmpty() && practice.isEmpty()) {
            return "暂无足够学习数据：先完成一次练习或课程学习，系统将基于真实掌握度生成下一步建议。";
        }
        List<String> parts = new ArrayList<>();
        if (!weak.isEmpty()) {
            List<String> names = weak.stream().map(NextStepVO.WeakKnowledgeVO::name)
                    .filter(Objects::nonNull).limit(3).toList();
            parts.add("优先复习薄弱知识点：" + String.join("、", names));
        }
        if (!courses.isEmpty()) {
            List<String> titles = courses.stream().map(NextStepVO.RecommendedCourseVO::title).toList();
            parts.add("学习覆盖这些知识点的课程：《" + String.join("》《", titles) + "》");
        }
        if (!practice.isEmpty()) {
            parts.add("完成 " + practice.size() + " 道针对性练习巩固");
        }
        return "建议：" + String.join("；随后", parts) + "。";
    }
}
