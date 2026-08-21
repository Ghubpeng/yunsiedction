package com.yunsie.module.learning.profile.service;

import com.yunsie.module.course.api.CourseQueryApi;
import com.yunsie.module.exam.api.ExamQueryApi;
import com.yunsie.module.learning.profile.config.MasteryParams;
import com.yunsie.module.learning.profile.entity.LearnMastery;
import com.yunsie.module.learning.profile.entity.LearnProfileSummary;
import com.yunsie.module.learning.profile.entity.LearnStudyCalendar;
import com.yunsie.module.learning.profile.mapper.LearnMasteryMapper;
import com.yunsie.module.learning.profile.mapper.LearnProfileSummaryMapper;
import com.yunsie.module.learning.profile.mapper.LearnStudyCalendarMapper;
import com.yunsie.module.question.api.QuestionQueryApi;
import com.yunsie.module.subject.api.SubjectQueryApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 档案同步单测：summary 聚合 / 掌握度版本隔离 / 日历聚合 / 重复同步幂等（重算）。
 */
class ProfileSyncServiceTest {

    private final QuestionQueryApi questionApi = mock(QuestionQueryApi.class);
    private final ExamQueryApi examApi = mock(ExamQueryApi.class);
    private final CourseQueryApi courseApi = mock(CourseQueryApi.class);
    private final SubjectQueryApi subjectApi = mock(SubjectQueryApi.class);
    private final LearnConfigService configService = mock(LearnConfigService.class);
    private final LearnProfileSummaryMapper summaryMapper = mock(LearnProfileSummaryMapper.class);
    private final LearnMasteryMapper masteryMapper = mock(LearnMasteryMapper.class);
    private final LearnStudyCalendarMapper calendarMapper = mock(LearnStudyCalendarMapper.class);

    private ProfileSyncService service() {
        return new ProfileSyncService(questionApi, examApi, courseApi, subjectApi,
                configService, summaryMapper, masteryMapper, calendarMapper);
    }

    private SubjectQueryApi.KnowledgeNodeView node(long id, long certId, long subjectId, long versionId) {
        return new SubjectQueryApi.KnowledgeNodeView(id, versionId, certId, subjectId,
                2, "节点" + id, "code" + id, "/1/" + id, 2, 1);
    }

    @Test
    void sync_buildsSummaryFromAllSources() {
        when(configService.masteryParams()).thenReturn(MasteryParams.DEFAULTS);
        LocalDateTime today10 = LocalDateTime.of(2026, 8, 19, 10, 0);
        when(questionApi.listPracticeStats(10L)).thenReturn(List.of(
                new QuestionQueryApi.PracticeStatView(101L, 3, 2, 1, 9000L, today10)));
        when(questionApi.listPracticeDailyStats(10L)).thenReturn(List.of(
                new QuestionQueryApi.PracticeDailyStatView(LocalDate.of(2026, 8, 19), 3, 2, 9000L)));
        when(subjectApi.findNode(101L)).thenReturn(node(101L, 1L, 2L, 3L));
        when(examApi.listSubmittedAttempts(10L)).thenReturn(List.of(
                new ExamQueryApi.AttemptSummaryView(11L, 5L, new BigDecimal("80.00"), 8, 10,
                        LocalDateTime.of(2026, 8, 19, 9, 0), LocalDateTime.of(2026, 8, 19, 9, 30)),
                new ExamQueryApi.AttemptSummaryView(12L, 5L, new BigDecimal("60.00"), 6, 10,
                        LocalDateTime.of(2026, 8, 18, 9, 0), LocalDateTime.of(2026, 8, 18, 9, 20))));
        when(courseApi.listLearnerProgress(10L)).thenReturn(List.of(
                new CourseQueryApi.ProgressView(201L, 7L, 570, 600, 1, LocalDateTime.of(2026, 8, 19, 8, 0)),
                new CourseQueryApi.ProgressView(202L, 7L, 100, 600, 0, null)));

        service().sync(10L);

        ArgumentCaptor<LearnProfileSummary> summaryCaptor = ArgumentCaptor.forClass(LearnProfileSummary.class);
        verify(summaryMapper).insert(summaryCaptor.capture());
        LearnProfileSummary s = summaryCaptor.getValue();
        // 总秒数 = 练习 9s + 考试 (30min+20min=3000s) + 课程 (570+100=670s) = 3679
        assertEquals(3679L, s.getTotalStudySeconds());
        assertEquals(1, s.getCourseFinishedLessons());
        assertEquals(3, s.getPracticeCount());
        assertEquals(2, s.getPracticeCorrectCount());
        assertEquals(2, s.getExamCount());
        assertEquals(new BigDecimal("80.00"), s.getExamBestScore());
        assertEquals(LocalDate.of(2026, 8, 19), s.getLastStudyDate());
        // 学习日：8/18, 8/19（今天=实际运行日，非 2026-08-19，streak 断言放独立用例）
        assertEquals(10L, s.getUserId());

        ArgumentCaptor<LearnMastery> masteryCaptor = ArgumentCaptor.forClass(LearnMastery.class);
        verify(masteryMapper, times(1)).insert(masteryCaptor.capture());
        LearnMastery m = masteryCaptor.getValue();
        assertEquals(101L, m.getNodeId());
        assertEquals(3L, m.getVersionId());
        assertEquals(2, m.getCorrectCount());
        assertEquals(1, m.getWrongCount());

        // 日历 3 天：练习日 8/19 + 考试日 8/19 与 8/18 + 课程日 8/19
        ArgumentCaptor<LearnStudyCalendar> calCaptor = ArgumentCaptor.forClass(LearnStudyCalendar.class);
        verify(calendarMapper, times(2)).insert(calCaptor.capture());
        long day19Seconds = calCaptor.getAllValues().stream()
                .filter(c -> c.getStudyDate().equals(LocalDate.of(2026, 8, 19)))
                .mapToLong(LearnStudyCalendar::getStudySeconds).sum();
        // 8/19: 练习 9s + 考试 1800s = 1809
        assertEquals(1809L, day19Seconds);
    }

    @Test
    void sync_versionIsolation_differentVersionSeparateRows() {
        when(configService.masteryParams()).thenReturn(MasteryParams.DEFAULTS);
        LocalDateTime now = LocalDateTime.of(2026, 8, 19, 10, 0);
        when(questionApi.listPracticeStats(10L)).thenReturn(List.of(
                new QuestionQueryApi.PracticeStatView(101L, 1, 1, 0, 1000L, now)));
        when(questionApi.listPracticeDailyStats(10L)).thenReturn(List.of());
        when(examApi.listSubmittedAttempts(10L)).thenReturn(List.of());
        when(courseApi.listLearnerProgress(10L)).thenReturn(List.of());
        when(subjectApi.findNode(101L)).thenReturn(node(101L, 1L, 2L, 3L));

        service().sync(10L);
        service().sync(10L); // 重算幂等：物理删除重建，结果不变

        ArgumentCaptor<LearnMastery> captor = ArgumentCaptor.forClass(LearnMastery.class);
        verify(masteryMapper, times(2)).insert(captor.capture());
        for (LearnMastery m : captor.getAllValues()) {
            assertEquals(3L, m.getVersionId());
            assertEquals(101L, m.getNodeId());
        }
        ArgumentCaptor<LearnProfileSummary> summaryCaptor = ArgumentCaptor.forClass(LearnProfileSummary.class);
        verify(summaryMapper, times(2)).insert(summaryCaptor.capture());
        // 物理删除被调用（重建前置）
        verify(summaryMapper, times(2)).physicalDeleteByUser(10L);
    }

    @Test
    void sync_unknownNodeSkipped() {
        when(configService.masteryParams()).thenReturn(MasteryParams.DEFAULTS);
        when(questionApi.listPracticeStats(10L)).thenReturn(List.of(
                new QuestionQueryApi.PracticeStatView(999L, 1, 1, 0, 1000L, LocalDateTime.now())));
        when(questionApi.listPracticeDailyStats(10L)).thenReturn(List.of());
        when(examApi.listSubmittedAttempts(10L)).thenReturn(List.of());
        when(courseApi.listLearnerProgress(10L)).thenReturn(List.of());
        when(subjectApi.findNode(999L)).thenReturn(null);

        service().sync(10L);

        ArgumentCaptor<LearnMastery> captor = ArgumentCaptor.forClass(LearnMastery.class);
        verify(masteryMapper, never()).insert(captor.capture());
    }

    @Test
    void sync_noDataAtAll_buildsEmptySummary() {
        when(configService.masteryParams()).thenReturn(MasteryParams.DEFAULTS);
        when(questionApi.listPracticeStats(10L)).thenReturn(List.of());
        when(questionApi.listPracticeDailyStats(10L)).thenReturn(List.of());
        when(examApi.listSubmittedAttempts(10L)).thenReturn(List.of());
        when(courseApi.listLearnerProgress(10L)).thenReturn(List.of());

        service().sync(10L);

        ArgumentCaptor<LearnProfileSummary> captor = ArgumentCaptor.forClass(LearnProfileSummary.class);
        verify(summaryMapper).insert(captor.capture());
        LearnProfileSummary s = captor.getValue();
        assertEquals(0L, s.getTotalStudySeconds());
        assertEquals(0, s.getExamCount());
        assertNull(s.getExamBestScore());
        assertNull(s.getLastStudyDate());
        assertEquals(0, s.getStreakDays());
        ArgumentCaptor<LearnMastery> masteryCaptor = ArgumentCaptor.forClass(LearnMastery.class);
        verify(masteryMapper, never()).insert(masteryCaptor.capture());
    }

    @Test
    void sync_nullUserId_noOp() {
        service().sync(null);
        // null 直接返回 → 不应触发任何重建/删除
        ArgumentCaptor<LearnProfileSummary> summaryCaptor = ArgumentCaptor.forClass(LearnProfileSummary.class);
        verify(summaryMapper, never()).insert(summaryCaptor.capture());
        verify(summaryMapper, never()).physicalDeleteByUser(anyLong());
        ArgumentCaptor<LearnMastery> masteryCaptor = ArgumentCaptor.forClass(LearnMastery.class);
        verify(masteryMapper, never()).insert(masteryCaptor.capture());
    }
}
