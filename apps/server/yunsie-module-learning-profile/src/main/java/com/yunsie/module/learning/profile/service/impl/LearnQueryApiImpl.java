package com.yunsie.module.learning.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.learning.profile.api.LearnQueryApi;
import com.yunsie.module.learning.profile.entity.LearnMastery;
import com.yunsie.module.learning.profile.entity.LearnProfileSummary;
import com.yunsie.module.learning.profile.mapper.LearnMasteryMapper;
import com.yunsie.module.learning.profile.mapper.LearnProfileSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * LearnQueryApi 实现：未来 AI 上下文注入的最小视图（本阶段无消费者）。
 * 只读最小必要字段；不做读时同步（AI 注入容忍秒级最终一致，learning-profile §6）。
 */
@Service
@RequiredArgsConstructor
public class LearnQueryApiImpl implements LearnQueryApi {

    private final LearnProfileSummaryMapper summaryMapper;
    private final LearnMasteryMapper masteryMapper;

    @Override
    public SummaryView findSummary(Long userId) {
        if (userId == null) {
            return null;
        }
        LearnProfileSummary row = summaryMapper.selectOne(new LambdaQueryWrapper<LearnProfileSummary>()
                .eq(LearnProfileSummary::getUserId, userId).last("LIMIT 1"));
        if (row == null) {
            return null;
        }
        return new SummaryView(row.getUserId(), row.getTotalStudySeconds(), row.getCourseFinishedLessons(),
                row.getPracticeCount(), row.getPracticeCorrectCount(), row.getExamCount(),
                row.getExamBestScore(), row.getStreakDays(), row.getLastStudyDate());
    }

    @Override
    public List<MasteryView> listMastery(Long userId, Long certificateId) {
        if (userId == null) {
            return List.of();
        }
        var wrapper = new LambdaQueryWrapper<LearnMastery>().eq(LearnMastery::getUserId, userId);
        if (certificateId != null) {
            wrapper.eq(LearnMastery::getCertificateId, certificateId);
        }
        return masteryMapper.selectList(wrapper).stream()
                .map(m -> new MasteryView(m.getNodeId(), m.getCertificateId(), m.getVersionId(),
                        m.getMasteryValue(), m.getCorrectCount(), m.getWrongCount(), m.getLastPracticeAt()))
                .toList();
    }

    @Override
    public List<WeaknessView> listWeakness(Long userId, int limit) {
        if (userId == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(50, limit));
        return masteryMapper.selectList(new LambdaQueryWrapper<LearnMastery>()
                        .eq(LearnMastery::getUserId, userId)
                        .orderByAsc(LearnMastery::getMasteryValue)
                        .last("LIMIT " + safeLimit))
                .stream()
                .map(m -> new WeaknessView(m.getNodeId(), m.getMasteryValue(), m.getWrongCount(),
                        m.getCorrectCount() + m.getWrongCount(), m.getLastPracticeAt()))
                .toList();
    }
}
