package com.yunsie.module.learning.profile.service;

import com.yunsie.module.course.api.CourseQueryApi;
import com.yunsie.module.exam.api.ExamQueryApi;
import com.yunsie.module.learning.profile.algorithm.MasteryCalculator;
import com.yunsie.module.learning.profile.algorithm.StreakCalculator;
import com.yunsie.module.learning.profile.config.MasteryParams;
import com.yunsie.module.learning.profile.entity.LearnMastery;
import com.yunsie.module.learning.profile.entity.LearnProfileSummary;
import com.yunsie.module.learning.profile.entity.LearnStudyCalendar;
import com.yunsie.module.learning.profile.enums.TriggerSource;
import com.yunsie.module.learning.profile.mapper.LearnMasteryMapper;
import com.yunsie.module.learning.profile.mapper.LearnProfileSummaryMapper;
import com.yunsie.module.learning.profile.mapper.LearnStudyCalendarMapper;
import com.yunsie.module.question.api.QuestionQueryApi;
import com.yunsie.module.question.api.QuestionQueryApi.PracticeDailyStatView;
import com.yunsie.module.question.api.QuestionQueryApi.PracticeStatView;
import com.yunsie.module.subject.api.SubjectQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 档案同步服务（全量重建，幂等）：
 *   来源仅经 QueryApi（question 练习 / exam 交卷 / course 进度）+ 节点版本经 SubjectQueryApi。
 *   重算 = 物理删除本域 learn_* 行后按源数据重建；不改任何其他域数据。
 *
 * <p>触发路径（三者共用）：
 *   1) ExamFinishedEvent 异步消费（事件驱动）
 *   2) 档案读接口读时同步（练习/课程无事件源 → 读时刷新，保证"练习→档案"即时可见）
 *   3) 管理员 recalc 兜底（崩溃窗口丢失事件的自愈，CONFLICTS #18）
 *
 * <p>并发：同一用户经进程内锁串行（单体单实例 MVP）；跨实例并发由事务+唯一键兜底。</p>
 */
@Service
@RequiredArgsConstructor
public class ProfileSyncService {

    private final QuestionQueryApi questionQueryApi;
    private final ExamQueryApi examQueryApi;
    private final CourseQueryApi courseQueryApi;
    private final SubjectQueryApi subjectQueryApi;
    private final LearnConfigService configService;
    private final LearnProfileSummaryMapper summaryMapper;
    private final LearnMasteryMapper masteryMapper;
    private final LearnStudyCalendarMapper calendarMapper;

    /** 按用户进程内锁（防同用户并发重建互相踩踏） */
    private final Map<Long, Object> userLocks = new ConcurrentHashMap<>();

    /**
     * 全量重建某用户档案（幂等；异常回滚，保留重建前数据）。
     * READ_COMMITTED：异步消费者可能在考试事务提交前启动，逐语句读取最新已提交数据，
     * 缩小"事件先于提交处理"的滞后窗口（仍可能滞后 → 读时同步自愈，CONFLICTS #18）。
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void sync(Long userId) {
        if (userId == null) {
            return;
        }
        Object lock = userLocks.computeIfAbsent(userId, k -> new Object());
        synchronized (lock) {
            rebuild(userId);
        }
    }

    private void rebuild(Long userId) {
        LocalDate today = LocalDate.now();
        MasteryParams params = configService.masteryParams();
        var practiceStats = questionQueryApi.listPracticeStats(userId);
        var dailyStats = questionQueryApi.listPracticeDailyStats(userId);
        var attempts = examQueryApi.listSubmittedAttempts(userId);
        var progress = courseQueryApi.listLearnerProgress(userId);

        summaryMapper.physicalDeleteByUser(userId);
        masteryMapper.physicalDeleteByUser(userId);
        calendarMapper.physicalDeleteByUser(userId);

        // ---------- 日历聚合：date -> [studySeconds, practiceCount, examCount] ----------
        Map<LocalDate, long[]> days = new LinkedHashMap<>();
        long practiceTotalMs = 0L;
        int practiceTotal = 0;
        int practiceCorrectTotal = 0;
        for (PracticeDailyStatView d : dailyStats) {
            long[] s = days.computeIfAbsent(d.studyDate(), k -> new long[3]);
            s[0] += d.totalResponseMs() / 1000;
            s[1] += d.practiceCount();
            practiceTotal += d.practiceCount();
            practiceCorrectTotal += d.correctCount();
            practiceTotalMs += d.totalResponseMs();
        }

        // ---------- 课程：完成小节数 + 总学习秒数（位置秒近似）+ 学习日标记 ----------
        long courseSeconds = 0L;
        int finishedLessons = 0;
        for (CourseQueryApi.ProgressView p : progress) {
            courseSeconds += Math.max(0, p.positionSeconds() == null ? 0 : p.positionSeconds());
            if (p.finished() != null && p.finished() == 1) {
                finishedLessons++;
            }
            if (p.lastLearnTime() != null) {
                // 课程学习历史无逐日事件（仅最近学习时间），只标记学习日（对连续学习天数有效）
                days.computeIfAbsent(p.lastLearnTime().toLocalDate(), k -> new long[3]);
            }
        }

        // ---------- 考试：时长（按交卷日计入）+ 最佳成绩 ----------
        long examSeconds = 0L;
        int examCount = attempts.size();
        BigDecimal bestScore = null;
        for (ExamQueryApi.AttemptSummaryView a : attempts) {
            long dur = durationSeconds(a.startedAt(), a.submittedAt());
            examSeconds += dur;
            if (a.submittedAt() != null) {
                long[] s = days.computeIfAbsent(a.submittedAt().toLocalDate(), k -> new long[3]);
                s[0] += dur;
                s[2]++;
            }
            if (a.score() != null && (bestScore == null || a.score().compareTo(bestScore) > 0)) {
                bestScore = a.score();
            }
        }
        for (Map.Entry<LocalDate, long[]> e : days.entrySet()) {
            LearnStudyCalendar row = new LearnStudyCalendar();
            row.setUserId(userId);
            row.setStudyDate(e.getKey());
            row.setStudySeconds(e.getValue()[0]);
            row.setPracticeCount((int) e.getValue()[1]);
            row.setExamCount((int) e.getValue()[2]);
            calendarMapper.insert(row);
        }

        // ---------- 掌握度：user x node x version 隔离 ----------
        for (PracticeStatView stat : practiceStats) {
            SubjectQueryApi.KnowledgeNodeView node = subjectQueryApi.findNode(stat.nodeId());
            if (node == null) {
                continue; // 节点不存在/已删除：无法定位版本，跳过（版本隔离铁律）
            }
            LearnMastery m = new LearnMastery();
            m.setUserId(userId);
            m.setCertificateId(node.certificateId());
            m.setSubjectId(node.subjectId());
            m.setNodeId(node.id());
            m.setVersionId(node.versionId());
            m.setCorrectCount(stat.correctCount());
            m.setWrongCount(stat.wrongCount());
            m.setLastPracticeAt(stat.lastPracticeAt());
            m.setLastTriggerSource(TriggerSource.PRACTICE.code());
            m.setMasteryValue(MasteryCalculator.aggregate(
                    stat.correctCount(), stat.wrongCount(), stat.lastPracticeAt(), today, params));
            masteryMapper.insert(m);
        }

        // ---------- summary ----------
        Set<LocalDate> studyDates = days.keySet();
        LearnProfileSummary summary = new LearnProfileSummary();
        summary.setUserId(userId);
        summary.setTotalStudySeconds(practiceTotalMs / 1000 + examSeconds + courseSeconds);
        summary.setCourseFinishedLessons(finishedLessons);
        summary.setPracticeCount(practiceTotal);
        summary.setPracticeCorrectCount(practiceCorrectTotal);
        summary.setExamCount(examCount);
        summary.setExamBestScore(bestScore);
        summary.setStreakDays(StreakCalculator.currentStreak(studyDates, today));
        summary.setLastStudyDate(studyDates.stream().max(LocalDate::compareTo).orElse(null));
        summaryMapper.insert(summary);
    }

    private static long durationSeconds(LocalDateTime startedAt, LocalDateTime submittedAt) {
        if (startedAt == null || submittedAt == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(startedAt, submittedAt).getSeconds());
    }
}
