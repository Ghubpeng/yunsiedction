package com.yunsie.module.learning.profile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.exam.api.ExamQueryApi;
import com.yunsie.module.learning.profile.algorithm.PredictionCalculator;
import com.yunsie.module.learning.profile.algorithm.PredictionCalculator.PredictionResult;
import com.yunsie.module.learning.profile.config.MasteryParams.PredictionParams;
import com.yunsie.module.learning.profile.entity.LearnMastery;
import com.yunsie.module.learning.profile.entity.LearnProfileSummary;
import com.yunsie.module.learning.profile.error.LearnErrorCode;
import com.yunsie.module.learning.profile.mapper.LearnMasteryMapper;
import com.yunsie.module.learning.profile.mapper.LearnProfileSummaryMapper;
import com.yunsie.module.learning.profile.vo.PredictionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 考试通过率预测（规则版，GET 时计算，不落库）：
 * 输入 = 历史考试得分（ExamQueryApi）+ 当前版本平均掌握度 + 练习量；
 * 仅参考展示、版本化、不参与任何自动决策、无"保证通过"语义（learning-profile §7）。
 */
@Service
@RequiredArgsConstructor
public class PredictionService {

    private final ProfileSyncService syncService;
    private final LearnConfigService configService;
    private final ExamQueryApi examQueryApi;
    private final LearnProfileSummaryMapper summaryMapper;
    private final LearnMasteryMapper masteryMapper;
    private final com.yunsie.module.subject.api.SubjectQueryApi subjectQueryApi;

    public PredictionVO predict(Long userId, Long examId) {
        syncService.sync(userId);
        ExamQueryApi.ExamView exam = examQueryApi.findExam(examId);
        if (exam == null) {
            throw new BizException(LearnErrorCode.EXAM_NOT_FOUND);
        }
        List<ExamQueryApi.AttemptSummaryView> attempts = examQueryApi.listSubmittedAttempts(userId);
        List<BigDecimal> scores = attempts.stream()
                .map(ExamQueryApi.AttemptSummaryView::score)
                .filter(java.util.Objects::nonNull)
                .toList(); // 按交卷时间倒序（QueryApi 保证）

        double avgMastery = averageMastery(userId);
        LearnProfileSummary summary = summaryMapper.selectOne(new LambdaQueryWrapper<LearnProfileSummary>()
                .eq(LearnProfileSummary::getUserId, userId).last("LIMIT 1"));
        int practiceCount = summary == null || summary.getPracticeCount() == null ? 0 : summary.getPracticeCount();

        PredictionParams params = configService.predictionParams();
        PredictionResult result = PredictionCalculator.predict(scores, exam.passScore(), avgMastery, practiceCount, params);
        return new PredictionVO(exam.id(), exam.name(), result.probability(), result.ruleVersion(),
                result.basis(), result.lowSample(), LocalDateTime.now());
    }

    /** 当前版本掌握度平均值（0~100；无掌握度记录=0） */
    private double averageMastery(Long userId) {
        List<LearnMastery> rows = masteryMapper.selectList(new LambdaQueryWrapper<LearnMastery>()
                .eq(LearnMastery::getUserId, userId));
        if (rows.isEmpty()) {
            return 0.0;
        }
        // 仅统计各证书当前版本（版本隔离）
        Map<Long, Long> currentByCert = new HashMap<>();
        for (LearnMastery m : rows) {
            currentByCert.computeIfAbsent(m.getCertificateId(), subjectQueryApi::findCurrentVersionId);
        }
        return rows.stream()
                .filter(m -> currentByCert.containsKey(m.getCertificateId()))
                .filter(m -> m.getVersionId() != null && m.getVersionId().equals(currentByCert.get(m.getCertificateId())))
                .mapToInt(m -> m.getMasteryValue() == null ? 0 : m.getMasteryValue())
                .average()
                .orElse(0.0);
    }
}
