package com.yunsie.module.learning.profile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.learning.profile.entity.LearnMastery;
import com.yunsie.module.learning.profile.mapper.LearnMasteryMapper;
import com.yunsie.module.learning.profile.vo.WeaknessVO;
import com.yunsie.module.question.api.QuestionQueryApi;
import com.yunsie.module.subject.api.SubjectQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 薄弱点服务：读时派生（不建 weakness 表）=
 *   learn_mastery（当前版本） + question 练习/错题聚合。
 * 排序：掌握度升序 → 错题数降序 → 最近练习时间升序（越久远越靠前）。
 */
@Service
@RequiredArgsConstructor
public class WeaknessService {

    private final ProfileSyncService syncService;
    private final LearnMasteryMapper masteryMapper;
    private final SubjectQueryApi subjectQueryApi;
    private final QuestionQueryApi questionQueryApi;
    private final LearnConfigService configService;

    public List<WeaknessVO> weakness(Long userId, Integer limit) {
        syncService.sync(userId);
        return weaknessInternal(userId, limit);
    }

    /** 薄弱点（教师视图复用；不做读时同步，由调用方保证已同步） */
    public List<WeaknessVO> weaknessOf(Long userId, Integer limit) {
        return weaknessInternal(userId, limit);
    }

    private List<WeaknessVO> weaknessInternal(Long userId, Integer limit) {
        int safeLimit = limit == null ? configService.weaknessDefaultLimit() : Math.max(1, Math.min(50, limit));

        List<LearnMastery> rows = masteryMapper.selectList(new LambdaQueryWrapper<LearnMastery>()
                .eq(LearnMastery::getUserId, userId));
        if (rows.isEmpty()) {
            return List.of();
        }

        // 仅保留各证书"当前版本"的掌握度行（版本切换后旧版本不参与薄弱点）
        Map<Long, Long> currentVersionByCert = new HashMap<>();
        for (LearnMastery m : rows) {
            currentVersionByCert.computeIfAbsent(m.getCertificateId(),
                    subjectQueryApi::findCurrentVersionId);
        }
        List<LearnMastery> currentRows = rows.stream()
                .filter(m -> {
                    Long current = currentVersionByCert.get(m.getCertificateId());
                    return current != null && current.equals(m.getVersionId());
                })
                .toList();

        // 练习/错题聚合（按节点）
        Map<Long, QuestionQueryApi.PracticeStatView> practiceByNode = new HashMap<>();
        questionQueryApi.listPracticeStats(userId)
                .forEach(p -> practiceByNode.put(p.nodeId(), p));
        Map<Long, QuestionQueryApi.MistakeStatView> mistakeByNode = new HashMap<>();
        questionQueryApi.listMistakeStats(userId)
                .forEach(m -> mistakeByNode.put(m.nodeId(), m));

        // 节点视图（名称/路径）
        Map<Long, SubjectQueryApi.KnowledgeNodeView> nodes = new HashMap<>();
        for (LearnMastery m : currentRows) {
            nodes.computeIfAbsent(m.getNodeId(), id -> subjectQueryApi.findNode(id));
        }

        return currentRows.stream()
                .map(m -> {
                    QuestionQueryApi.PracticeStatView p = practiceByNode.get(m.getNodeId());
                    QuestionQueryApi.MistakeStatView mk = mistakeByNode.get(m.getNodeId());
                    SubjectQueryApi.KnowledgeNodeView n = nodes.get(m.getNodeId());
                    int wrong = (p == null ? 0 : p.wrongCount()) + (mk == null ? 0 : mk.mistakeCount());
                    int practice = p == null ? 0 : p.practiceCount();
                    LocalDateTime lastAt = m.getLastPracticeAt();
                    return new WeaknessVO(m.getNodeId(),
                            n == null ? null : n.name(), n == null ? null : n.path(),
                            n == null ? null : n.nodeType(),
                            m.getMasteryValue(), wrong, practice, lastAt);
                })
                .sorted(Comparator.comparing(WeaknessVO::mastery)
                        .thenComparing(Comparator.comparing(WeaknessVO::wrongCount).reversed())
                        .thenComparing(WeaknessVO::lastPracticeAt,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(safeLimit)
                .toList();
    }
}
