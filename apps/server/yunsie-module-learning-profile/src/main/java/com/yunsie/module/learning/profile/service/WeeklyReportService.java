package com.yunsie.module.learning.profile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.course.api.CourseQueryApi;
import com.yunsie.module.learning.profile.entity.LearnMastery;
import com.yunsie.module.learning.profile.entity.LearnStudyCalendar;
import com.yunsie.module.learning.profile.mapper.LearnMasteryMapper;
import com.yunsie.module.learning.profile.mapper.LearnStudyCalendarMapper;
import com.yunsie.module.learning.profile.vo.WeeklyReportVO;
import com.yunsie.module.question.api.QuestionQueryApi;
import com.yunsie.module.subject.api.SubjectQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 周学习报告（Stage 2.4）：最近 7 天（含今日）的学习时间/完成章节/练习数量/掌握变化。
 * 全部为规则聚合真实数据；无历史掌握度快照 → masteredNodes 口径如实定义并随 VO 文档说明。
 */
@Service
@RequiredArgsConstructor
public class WeeklyReportService {

    private final LearnStudyCalendarMapper calendarMapper;
    private final LearnMasteryMapper masteryMapper;
    private final QuestionQueryApi questionQueryApi;
    private final CourseQueryApi courseQueryApi;
    private final SubjectQueryApi subjectQueryApi;

    public WeeklyReportVO report(Long userId) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(6);
        LocalDateTime since = start.atStartOfDay();

        // 学习时间：学习日历（练习耗时+考试时长；课程学习逐日无事件历史，仅学习日标记——如实口径）
        Long studySeconds = calendarMapper.selectList(new LambdaQueryWrapper<LearnStudyCalendar>()
                        .eq(LearnStudyCalendar::getUserId, userId)
                        .ge(LearnStudyCalendar::getStudyDate, start)
                        .le(LearnStudyCalendar::getStudyDate, end))
                .stream().mapToLong(d -> d.getStudySeconds() == null ? 0 : d.getStudySeconds()).sum();

        // 完成章节：窗口内 finished 课时的章节去重
        long finishedChapters = courseQueryApi.countFinishedChaptersInWindow(userId, since);

        // 练习数量：练习日聚合（近 7 天）
        List<QuestionQueryApi.PracticeDailyStatView> daily = questionQueryApi.listPracticeDailyStats(userId).stream()
                .filter(d -> d.studyDate() != null && !d.studyDate().isBefore(start) && !d.studyDate().isAfter(end))
                .toList();
        int practiceCount = daily.stream().mapToInt(d -> d.practiceCount() == null ? 0 : d.practiceCount()).sum();
        int practiceCorrect = daily.stream().mapToInt(d -> d.correctCount() == null ? 0 : d.correctCount()).sum();

        // 掌握变化：本周练习过的知识点中当前掌握度≥60（当前版本）的数量
        List<Long> practicedNodes = questionQueryApi.listPracticeStats(userId).stream()
                .filter(s -> s.lastPracticeAt() != null && !s.lastPracticeAt().isBefore(since))
                .map(QuestionQueryApi.PracticeStatView::nodeId)
                .toList();
        int mastered = 0;
        if (!practicedNodes.isEmpty()) {
            List<LearnMastery> rows = masteryMapper.selectList(new LambdaQueryWrapper<LearnMastery>()
                    .eq(LearnMastery::getUserId, userId)
                    .in(LearnMastery::getNodeId, practicedNodes));
            for (LearnMastery m : rows) {
                Long current = subjectQueryApi.findCurrentVersionId(m.getCertificateId());
                if (current != null && current.equals(m.getVersionId())
                        && m.getMasteryValue() != null && m.getMasteryValue() >= 60) {
                    mastered++;
                }
            }
        }
        return new WeeklyReportVO(start, end, studySeconds, (int) finishedChapters,
                practiceCount, practiceCorrect, mastered, practicedNodes.size());
    }
}
