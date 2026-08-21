package com.yunsie.module.learning.profile.service;

import com.yunsie.module.exam.api.ExamFinishedEvent;
import com.yunsie.module.learning.profile.entity.LearnEventDedup;
import com.yunsie.module.learning.profile.mapper.LearnEventDedupMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 事件消费幂等单测：重复事件不重复累计；同步失败不影响交卷（异常被吞掉）。
 * 注意：BaseMapper.insert 存在 insert(T)/insert(Collection) 重载，Mockito 一律经 ArgumentCaptor 指定类型。
 */
class ExamFinishedEventListenerTest {

    private final LearnEventDedupMapper dedupMapper = mock(LearnEventDedupMapper.class);
    private final ProfileSyncService syncService = mock(ProfileSyncService.class);

    private ExamFinishedEventListener listener() {
        return new ExamFinishedEventListener(dedupMapper, syncService);
    }

    private ExamFinishedEvent event() {
        return new ExamFinishedEvent(11L, 10L, 5L, new BigDecimal("80.00"), 8, 10, LocalDateTime.now());
    }

    @Test
    void firstEvent_dedupInserted_thenSync() {
        listener().onExamFinished(event());

        ArgumentCaptor<LearnEventDedup> captor = ArgumentCaptor.forClass(LearnEventDedup.class);
        verify(dedupMapper).insert(captor.capture());
        assertEquals("exam:11", captor.getValue().getDedupKey());
        assertEquals("EXAM_FINISHED", captor.getValue().getEventType());
        verify(syncService).sync(10L);
    }

    @Test
    void duplicateEvent_skipped() {
        ArgumentCaptor<LearnEventDedup> captor = ArgumentCaptor.forClass(LearnEventDedup.class);
        doThrow(new DuplicateKeyException("dup")).when(dedupMapper).insert(captor.capture());
        listener().onExamFinished(event());
        verify(syncService, never()).sync(any());
    }

    @Test
    void syncFailure_swallowed() {
        doThrow(new RuntimeException("db down")).when(syncService).sync(10L);
        // 不抛出：事件处理失败不得影响考试交卷（调用方不受影响）
        listener().onExamFinished(event());
        verify(syncService).sync(10L);
    }

    @Test
    void nullEvent_noOp() {
        listener().onExamFinished(null);
        ArgumentCaptor<LearnEventDedup> captor = ArgumentCaptor.forClass(LearnEventDedup.class);
        verify(dedupMapper, never()).insert(captor.capture());
        verify(syncService, never()).sync(any());
    }

    @Test
    void eventWithNullUser_skipped() {
        listener().onExamFinished(new ExamFinishedEvent(11L, null, 5L,
                new BigDecimal("80.00"), 8, 10, LocalDateTime.now()));
        ArgumentCaptor<LearnEventDedup> captor = ArgumentCaptor.forClass(LearnEventDedup.class);
        verify(dedupMapper, never()).insert(captor.capture());
        verify(syncService, never()).sync(any());
    }
}
