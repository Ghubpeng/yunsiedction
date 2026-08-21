package com.yunsie.module.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.question.api.QuestionQueryApi;
import com.yunsie.module.question.entity.QuestionKnowledgeNode;
import com.yunsie.module.question.entity.QuestionMistake;
import com.yunsie.module.question.entity.QuestionPracticeRecord;
import com.yunsie.module.question.mapper.QuestionKnowledgeNodeMapper;
import com.yunsie.module.question.mapper.QuestionMistakeMapper;
import com.yunsie.module.question.mapper.QuestionPracticeRecordMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stage 1.6 契约扩展测试：练习/错题聚合（追加式，不改既有方法）。
 * 聚合在实现内内存计算 → 直接对聚合结果断言；既有方法行为由既有测试继续覆盖。
 */
class QuestionQueryApiExtendedTest {

    private final QuestionPracticeRecordMapper practiceMapper = mock(QuestionPracticeRecordMapper.class);
    private final QuestionKnowledgeNodeMapper nodeMapper = mock(QuestionKnowledgeNodeMapper.class);
    private final QuestionMistakeMapper mistakeMapper = mock(QuestionMistakeMapper.class);

    private QuestionQueryApiImpl api() {
        return new QuestionQueryApiImpl(mock(com.yunsie.module.question.mapper.QuestionMapper.class),
                mock(com.yunsie.module.question.mapper.QuestionOptionMapper.class),
                nodeMapper, practiceMapper, mistakeMapper,
                mock(com.yunsie.module.question.service.GradingService.class));
    }

    private QuestionPracticeRecord record(long id, Long userId, Long nodeId, int correct, long ms, LocalDateTime at) {
        QuestionPracticeRecord r = new QuestionPracticeRecord();
        r.setId(id);
        r.setUserId(userId);
        r.setQuestionId(100L + id);
        r.setKnowledgeNodeId(nodeId);
        r.setCorrect(correct);
        r.setResponseTimeMs((int) ms);
        r.setAnswerTime(at);
        return r;
    }

    @Test
    void practiceStats_aggregatesByNode() {
        LocalDateTime t1 = LocalDateTime.of(2026, 8, 19, 9, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 8, 19, 10, 0);
        when(practiceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                record(1L, 10L, 101L, 1, 1000, t1),
                record(2L, 10L, 101L, 0, 2000, t2),
                record(3L, 10L, 102L, 1, 300, t1)));

        List<QuestionQueryApi.PracticeStatView> stats = api().listPracticeStats(10L);

        assertEquals(2, stats.size());
        QuestionQueryApi.PracticeStatView node101 = stats.stream().filter(s -> s.nodeId() == 101L).findFirst().orElseThrow();
        assertEquals(2, node101.practiceCount());
        assertEquals(1, node101.correctCount());
        assertEquals(1, node101.wrongCount());
        assertEquals(3000L, node101.totalResponseMs());
        assertEquals(t2, node101.lastPracticeAt());
        QuestionQueryApi.PracticeStatView node102 = stats.stream().filter(s -> s.nodeId() == 102L).findFirst().orElseThrow();
        assertEquals(1, node102.practiceCount());
        assertEquals(1, node102.correctCount());
    }

    @Test
    void practiceDailyStats_aggregatesByDate() {
        when(practiceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                record(1L, 10L, 101L, 1, 1000, LocalDateTime.of(2026, 8, 19, 9, 0)),
                record(2L, 10L, 101L, 0, 2000, LocalDateTime.of(2026, 8, 19, 10, 0)),
                record(3L, 10L, 102L, 1, 300, LocalDateTime.of(2026, 8, 18, 9, 0))));

        List<QuestionQueryApi.PracticeDailyStatView> daily = api().listPracticeDailyStats(10L);

        assertEquals(2, daily.size());
        var day19 = daily.stream().filter(d -> d.studyDate().getDayOfMonth() == 19).findFirst().orElseThrow();
        assertEquals(2, day19.practiceCount());
        assertEquals(1, day19.correctCount());
        assertEquals(3000L, day19.totalResponseMs());
        var day18 = daily.stream().filter(d -> d.studyDate().getDayOfMonth() == 18).findFirst().orElseThrow();
        assertEquals(1, day18.practiceCount());
        assertEquals(300L, day18.totalResponseMs());
    }

    @Test
    void mistakeStats_aggregatesByNode_unresolvedOnly() {
        QuestionMistake m1 = new QuestionMistake();
        m1.setUserId(10L);
        m1.setQuestionId(201L);
        m1.setMistakeCount(3);
        m1.setLastMistakeTime(LocalDateTime.of(2026, 8, 19, 9, 0));
        when(mistakeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(m1));
        QuestionKnowledgeNode link = new QuestionKnowledgeNode();
        link.setQuestionId(201L);
        link.setNodeId(101L);
        when(nodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(link));

        List<QuestionQueryApi.MistakeStatView> stats = api().listMistakeStats(10L);

        assertEquals(1, stats.size());
        assertEquals(101L, stats.get(0).nodeId());
        assertEquals(3, stats.get(0).mistakeCount());
        // status=1（未解决）过滤条件在 WHERE 子句，由 boot 集成测试（真实 MySQL）覆盖
    }

    @Test
    void nullUserId_returnsEmpty() {
        assertTrue(api().listPracticeStats(null).isEmpty());
        assertTrue(api().listPracticeDailyStats(null).isEmpty());
        assertTrue(api().listMistakeStats(null).isEmpty());
    }
}
