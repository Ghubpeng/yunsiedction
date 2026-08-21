package com.yunsie.module.learning.profile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.learning.profile.entity.LearnMastery;
import com.yunsie.module.learning.profile.mapper.LearnMasteryMapper;
import com.yunsie.module.learning.profile.vo.RecommendationVO;
import com.yunsie.module.question.api.QuestionQueryApi;
import com.yunsie.module.subject.api.SubjectQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 针对性练习推荐服务（基于真实数据，禁止 random）。
 * 节点优先级：①未解决错题节点 ②掌握度低节点（learn_mastery，当前版本）③长期未练节点；
 * 合并去重取前 N（≈min(5,limit)）节点，经 QuestionQueryApi.listPublishedByNodes 跨节点均衡取题（limit 道）；
 * 不足则诚实返回已有（禁止用证书维度 findPublishedQuestions 补齐）。
 */
@Service
@RequiredArgsConstructor
public class PracticeRecommendationService {

    private final ProfileSyncService syncService;
    private final LearnMasteryMapper masteryMapper;
    private final QuestionQueryApi questionQueryApi;
    private final SubjectQueryApi subjectQueryApi;

    public RecommendationVO recommend(Long userId, Long certificateId, int limit) {
        int safeLimit = Math.max(1, Math.min(50, limit));
        int nodeLimit = Math.min(5, safeLimit);
        syncService.sync(userId);

        List<Long> mistakeNodes = questionQueryApi.listMistakeStats(userId).stream()
                .sorted(Comparator.comparing(QuestionQueryApi.MistakeStatView::mistakeCount).reversed()
                        .thenComparing(QuestionQueryApi.MistakeStatView::lastMistakeTime,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .map(QuestionQueryApi.MistakeStatView::nodeId)
                .toList();
        List<Long> masteryNodes = masteryNodeIds(userId, certificateId);
        List<Long> staleNodes = questionQueryApi.listPracticeStats(userId).stream()
                .sorted(Comparator.comparing(QuestionQueryApi.PracticeStatView::lastPracticeAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(QuestionQueryApi.PracticeStatView::nodeId)
                .toList();

        Map<Long, SubjectQueryApi.KnowledgeNodeView> nodeCache = new HashMap<>();
        List<Long> priority = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        int[] from = new int[3];

        for (Long n : mistakeNodes) {
            if (priority.size() >= nodeLimit) {
                break;
            }
            if (!matchesCert(n, certificateId, nodeCache)) {
                continue;
            }
            if (seen.add(n)) {
                priority.add(n);
                from[0]++;
            }
        }
        for (Long n : masteryNodes) {
            if (priority.size() >= nodeLimit) {
                break;
            }
            if (!matchesCert(n, certificateId, nodeCache)) {
                continue;
            }
            if (seen.add(n)) {
                priority.add(n);
                from[1]++;
            }
        }
        for (Long n : staleNodes) {
            if (priority.size() >= nodeLimit) {
                break;
            }
            if (!matchesCert(n, certificateId, nodeCache)) {
                continue;
            }
            if (seen.add(n)) {
                priority.add(n);
                from[2]++;
            }
        }

        List<QuestionQueryApi.PracticeQuestionView> questions = balancedQuestions(priority, safeLimit);
        return new RecommendationVO(reason(from), priority.stream()
                .map(id -> {
                    SubjectQueryApi.KnowledgeNodeView n = nodeCache.computeIfAbsent(id, subjectQueryApi::findNode);
                    return new RecommendationVO.NodeItem(id, n == null ? null : n.name());
                })
                .toList(), questions);
    }

    /** 掌握度低节点：learn_mastery（各证书当前版本，版本隔离）按 mastery_value 升序 */
    private List<Long> masteryNodeIds(Long userId, Long certificateId) {
        LambdaQueryWrapper<LearnMastery> wrapper = new LambdaQueryWrapper<LearnMastery>()
                .eq(LearnMastery::getUserId, userId);
        if (certificateId != null) {
            wrapper.eq(LearnMastery::getCertificateId, certificateId);
        }
        List<LearnMastery> rows = masteryMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> currentByCert = new HashMap<>();
        for (LearnMastery m : rows) {
            currentByCert.computeIfAbsent(m.getCertificateId(), subjectQueryApi::findCurrentVersionId);
        }
        return rows.stream()
                .filter(m -> {
                    Long current = currentByCert.get(m.getCertificateId());
                    return current != null && current.equals(m.getVersionId());
                })
                .sorted(Comparator.comparing(LearnMastery::getMasteryValue,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(LearnMastery::getNodeId)
                .toList();
    }

    private boolean matchesCert(Long nodeId, Long certificateId,
                                Map<Long, SubjectQueryApi.KnowledgeNodeView> cache) {
        if (certificateId == null) {
            return true;
        }
        SubjectQueryApi.KnowledgeNodeView n = cache.computeIfAbsent(nodeId, subjectQueryApi::findNode);
        return n != null && certificateId.equals(n.certificateId());
    }

    /** 跨节点均衡取题（round-robin；不足返回已有，不补齐） */
    private List<QuestionQueryApi.PracticeQuestionView> balancedQuestions(List<Long> priority, int limit) {
        if (priority.isEmpty()) {
            return List.of();
        }
        int perNode = Math.max(1, (int) Math.ceil((double) limit / priority.size()));
        List<List<QuestionQueryApi.PracticeQuestionView>> perNodeQuestions = new ArrayList<>();
        for (Long nodeId : priority) {
            List<QuestionQueryApi.PracticeQuestionView> qs =
                    questionQueryApi.listPublishedByNodes(List.of(nodeId), perNode);
            if (!qs.isEmpty()) {
                perNodeQuestions.add(qs);
            }
        }
        List<QuestionQueryApi.PracticeQuestionView> result = new ArrayList<>();
        Set<Long> seenQ = new HashSet<>();
        int idx = 0;
        while (result.size() < limit) {
            boolean any = false;
            for (List<QuestionQueryApi.PracticeQuestionView> qs : perNodeQuestions) {
                if (result.size() >= limit) {
                    break;
                }
                if (idx < qs.size()) {
                    any = true;
                    QuestionQueryApi.PracticeQuestionView q = qs.get(idx);
                    if (seenQ.add(q.id())) {
                        result.add(q);
                    }
                }
            }
            if (!any) {
                break;
            }
            idx++;
        }
        return result;
    }

    private String reason(int[] from) {
        List<String> parts = new ArrayList<>();
        if (from[0] > 0) {
            parts.add(from[0] + " 个错题知识点");
        }
        if (from[1] > 0) {
            parts.add(from[1] + " 个低掌握知识点");
        }
        if (from[2] > 0) {
            parts.add(from[2] + " 个长期未练知识点");
        }
        return parts.isEmpty() ? "暂无针对性练习数据" : "基于 " + String.join("、", parts);
    }
}
