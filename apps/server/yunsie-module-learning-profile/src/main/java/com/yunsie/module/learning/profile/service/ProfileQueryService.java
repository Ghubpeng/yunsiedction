package com.yunsie.module.learning.profile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.learning.profile.algorithm.StreakCalculator;
import com.yunsie.module.learning.profile.entity.LearnMastery;
import com.yunsie.module.learning.profile.entity.LearnProfileSummary;
import com.yunsie.module.learning.profile.entity.LearnStudyCalendar;
import com.yunsie.module.learning.profile.error.LearnErrorCode;
import com.yunsie.module.learning.profile.mapper.LearnMasteryMapper;
import com.yunsie.module.learning.profile.mapper.LearnProfileSummaryMapper;
import com.yunsie.module.learning.profile.mapper.LearnStudyCalendarMapper;
import com.yunsie.module.learning.profile.vo.CalendarVO;
import com.yunsie.module.learning.profile.vo.MasteryVO;
import com.yunsie.module.learning.profile.vo.SummaryVO;
import com.yunsie.module.learning.profile.vo.WeaknessVO;
import com.yunsie.module.subject.api.SubjectQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 档案读服务（本人视角）：读前触发同步（练习/课程无事件源 → 读时刷新保证"练习→档案"即时可见），
 * 然后从 learn_* 表组装视图。本人数据严格经 @CurrentUser 传入，禁止 userId 入参。
 */
@Service
@RequiredArgsConstructor
public class ProfileQueryService {

    private final ProfileSyncService syncService;
    private final WeaknessService weaknessService;
    private final LearnProfileSummaryMapper summaryMapper;
    private final LearnMasteryMapper masteryMapper;
    private final LearnStudyCalendarMapper calendarMapper;
    private final SubjectQueryApi subjectQueryApi;

    public SummaryVO summary(Long userId) {
        syncService.sync(userId);
        return toSummaryVO(requireSummary(userId));
    }

    /** 指定证书"当前版本"的掌握度列表（不同版本不混算；仅返回已有练习的节点） */
    public List<MasteryVO> mastery(Long userId, Long certificateId) {
        syncService.sync(userId);
        Long currentVersionId = subjectQueryApi.findCurrentVersionId(certificateId);
        if (currentVersionId == null) {
            return List.of();
        }
        List<LearnMastery> rows = masteryMapper.selectList(new LambdaQueryWrapper<LearnMastery>()
                .eq(LearnMastery::getUserId, userId)
                .eq(LearnMastery::getCertificateId, certificateId)
                .eq(LearnMastery::getVersionId, currentVersionId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, SubjectQueryApi.KnowledgeNodeView> nodes = loadNodes(rows.stream()
                .map(LearnMastery::getNodeId).distinct().toList());
        return rows.stream()
                .map(m -> {
                    SubjectQueryApi.KnowledgeNodeView n = nodes.get(m.getNodeId());
                    return new MasteryVO(m.getNodeId(),
                            n == null ? null : n.name(), n == null ? null : n.code(),
                            n == null ? null : n.path(), n == null ? null : n.nodeType(),
                            m.getCertificateId(), m.getSubjectId(), m.getVersionId(),
                            m.getMasteryValue(), m.getCorrectCount(), m.getWrongCount(), m.getLastPracticeAt());
                })
                .sorted(Comparator.comparing(MasteryVO::nodePath, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    public List<WeaknessVO> weakness(Long userId, Integer limit) {
        return weaknessService.weakness(userId, limit);
    }

    /** 全部证书"当前版本"的掌握度（本人视图；读时同步保证新鲜；Stage 1.8 追加） */
    public List<MasteryVO> masteryAll(Long userId) {
        syncService.sync(userId);
        return masteryOf(userId);
    }

    /** 学习日历（month=yyyy-MM，只读该月；连续学习天数基于全量学习日） */
    public CalendarVO calendar(Long userId, String month) {
        syncService.sync(userId);
        YearMonth ym = parseMonth(month);
        List<LearnStudyCalendar> monthRows = calendarMapper.selectList(new LambdaQueryWrapper<LearnStudyCalendar>()
                .eq(LearnStudyCalendar::getUserId, userId)
                .ge(LearnStudyCalendar::getStudyDate, ym.atDay(1))
                .le(LearnStudyCalendar::getStudyDate, ym.atEndOfMonth())
                .orderByAsc(LearnStudyCalendar::getStudyDate));
        var allDates = calendarMapper.selectList(new LambdaQueryWrapper<LearnStudyCalendar>()
                        .eq(LearnStudyCalendar::getUserId, userId)).stream()
                .map(LearnStudyCalendar::getStudyDate).collect(Collectors.toSet());
        LocalDate today = LocalDate.now();
        return new CalendarVO(month,
                StreakCalculator.currentStreak(allDates, today),
                StreakCalculator.longestStreak(allDates),
                monthRows.stream()
                        .map(r -> new CalendarVO.DayItem(r.getStudyDate(), r.getStudySeconds(),
                                r.getPracticeCount(), r.getExamCount()))
                        .toList());
    }

    /** 汇总视图（供教师/重算等复用；不做读时同步） */
    public SummaryVO summaryOf(Long userId) {
        return toSummaryVO(requireSummary(userId));
    }

    /** 用户全部证书"当前版本"的掌握度（教师视图；不做读时同步） */
    public List<MasteryVO> masteryOf(Long userId) {
        List<LearnMastery> rows = masteryMapper.selectList(new LambdaQueryWrapper<LearnMastery>()
                .eq(LearnMastery::getUserId, userId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> currentByCert = new java.util.HashMap<>();
        for (LearnMastery m : rows) {
            currentByCert.computeIfAbsent(m.getCertificateId(), subjectQueryApi::findCurrentVersionId);
        }
        List<LearnMastery> currentRows = rows.stream()
                .filter(m -> {
                    Long current = currentByCert.get(m.getCertificateId());
                    return current != null && current.equals(m.getVersionId());
                })
                .toList();
        if (currentRows.isEmpty()) {
            return List.of();
        }
        Map<Long, SubjectQueryApi.KnowledgeNodeView> nodes = loadNodes(currentRows.stream()
                .map(LearnMastery::getNodeId).distinct().toList());
        return currentRows.stream()
                .map(m -> {
                    SubjectQueryApi.KnowledgeNodeView n = nodes.get(m.getNodeId());
                    return new MasteryVO(m.getNodeId(),
                            n == null ? null : n.name(), n == null ? null : n.code(),
                            n == null ? null : n.path(), n == null ? null : n.nodeType(),
                            m.getCertificateId(), m.getSubjectId(), m.getVersionId(),
                            m.getMasteryValue(), m.getCorrectCount(), m.getWrongCount(), m.getLastPracticeAt());
                })
                .sorted(Comparator.comparing(MasteryVO::nodePath, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private LearnProfileSummary requireSummary(Long userId) {
        LearnProfileSummary row = summaryMapper.selectOne(new LambdaQueryWrapper<LearnProfileSummary>()
                .eq(LearnProfileSummary::getUserId, userId).last("LIMIT 1"));
        if (row == null) {
            throw new BizException(LearnErrorCode.PROFILE_NOT_FOUND);
        }
        return row;
    }

    private SummaryVO toSummaryVO(LearnProfileSummary row) {
        return new SummaryVO(row.getUserId(), row.getTotalStudySeconds(), row.getCourseFinishedLessons(),
                row.getPracticeCount(), row.getPracticeCorrectCount(), row.getExamCount(),
                row.getExamBestScore(), row.getStreakDays(), row.getLastStudyDate(), row.getUpdateTime());
    }

    private Map<Long, SubjectQueryApi.KnowledgeNodeView> loadNodes(List<Long> nodeIds) {
        return nodeIds.stream().map(subjectQueryApi::findNode)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(SubjectQueryApi.KnowledgeNodeView::id, Function.identity(), (a, b) -> a));
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BizException(LearnErrorCode.CALENDAR_MONTH_INVALID);
        }
    }
}
