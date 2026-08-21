package com.yunsie.module.learning.profile.controller;

import com.yunsie.common.api.Result;
import com.yunsie.common.security.CurrentUser;
import com.yunsie.module.learning.profile.service.NextStepRuleService;
import com.yunsie.module.learning.profile.service.PracticeRecommendationService;
import com.yunsie.module.learning.profile.service.PredictionService;
import com.yunsie.module.learning.profile.service.ProfileQueryService;
import com.yunsie.module.learning.profile.service.WeeklyReportService;
import com.yunsie.module.learning.profile.vo.CalendarVO;
import com.yunsie.module.learning.profile.vo.MasteryVO;
import com.yunsie.module.learning.profile.vo.NextStepVO;
import com.yunsie.module.learning.profile.vo.PredictionVO;
import com.yunsie.module.learning.profile.vo.RecommendationVO;
import com.yunsie.module.learning.profile.vo.SummaryVO;
import com.yunsie.module.learning.profile.vo.WeaknessVO;
import com.yunsie.module.learning.profile.vo.WeeklyReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学员本人学习档案接口（登录即可；严格本人数据，禁止 userId 入参，learning-profile §6）。
 */
@RestController
@RequestMapping("/api/v1/learn/me")
@RequiredArgsConstructor
public class LearnUserController {

    private final ProfileQueryService queryService;
    private final PredictionService predictionService;
    private final PracticeRecommendationService recommendationService;
    private final NextStepRuleService nextStepRuleService;
    private final WeeklyReportService weeklyReportService;

    @GetMapping("/summary")
    public Result<SummaryVO> summary(@CurrentUser Long userId) {
        return Result.ok(queryService.summary(userId));
    }

    @GetMapping("/mastery")
    public Result<List<MasteryVO>> mastery(@CurrentUser Long userId,
                                           @RequestParam(required = false) Long certificateId) {
        if (certificateId == null) {
            // Stage 1.8 追加（用户端 Web 无证书上下文）：返回全部证书当前版本掌握度
            return Result.ok(queryService.masteryAll(userId));
        }
        return Result.ok(queryService.mastery(userId, certificateId));
    }

    @GetMapping("/weakness")
    public Result<List<WeaknessVO>> weakness(@CurrentUser Long userId,
                                             @RequestParam(required = false) Integer limit) {
        return Result.ok(queryService.weakness(userId, limit));
    }

    @GetMapping("/calendar")
    public Result<CalendarVO> calendar(@CurrentUser Long userId,
                                       @RequestParam String month) {
        return Result.ok(queryService.calendar(userId, month));
    }

    @GetMapping("/prediction")
    public Result<PredictionVO> prediction(@CurrentUser Long userId,
                                           @RequestParam Long examId) {
        return Result.ok(predictionService.predict(userId, examId));
    }

    /** 针对性练习推荐（基于真实数据；不足则诚实返回已有） */
    @GetMapping("/practice-recommendation")
    public Result<RecommendationVO> practiceRecommendation(
            @CurrentUser Long userId,
            @RequestParam(required = false) Long certificateId,
            @RequestParam(defaultValue = "10") Integer limit) {
        return Result.ok(recommendationService.recommend(userId, certificateId, limit));
    }

    /** 学习下一步规则推荐（Stage 2.3B；无真实 LLM，纯规则） */
    @GetMapping("/next-step")
    public Result<NextStepVO> nextStep(@CurrentUser Long userId,
                                       @RequestParam(required = false) Long certificateId) {
        return Result.ok(nextStepRuleService.nextStep(userId, certificateId));
    }

    /** 周学习报告（Stage 2.4：学习时间/完成章节/练习数量/掌握变化，规则聚合真实数据） */
    @GetMapping("/weekly-report")
    public Result<WeeklyReportVO> weeklyReport(@CurrentUser Long userId) {
        return Result.ok(weeklyReportService.report(userId));
    }
}
