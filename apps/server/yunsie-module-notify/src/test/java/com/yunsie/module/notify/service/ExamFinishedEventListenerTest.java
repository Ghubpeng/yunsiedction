package com.yunsie.module.notify.service;

import com.yunsie.module.exam.api.ExamFinishedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * notify 事件消费单测：正常消费 / null 跳过 / 失败隔离（不影响交卷）。
 */
class ExamFinishedEventListenerTest {

    private final NotifyMessageService messageService = mock(NotifyMessageService.class);

    private ExamFinishedEventListener listener() {
        return new ExamFinishedEventListener(messageService);
    }

    @Test
    void examFinished_createsScoreNotice() {
        listener().onExamFinished(new ExamFinishedEvent(11L, 10L, 5L,
                new BigDecimal("80.00"), 8, 10, LocalDateTime.now()));
        verify(messageService).createScoreNotice(any(ExamFinishedEvent.class));
    }

    @Test
    void nullEvent_skipped() {
        listener().onExamFinished(null);
        verify(messageService, never()).createScoreNotice(any());
    }

    @Test
    void eventWithNullUser_skipped() {
        listener().onExamFinished(new ExamFinishedEvent(11L, null, 5L,
                new BigDecimal("80.00"), 8, 10, LocalDateTime.now()));
        verify(messageService, never()).createScoreNotice(any());
    }

    @Test
    void failure_swallowed() {
        doThrow(new RuntimeException("db down")).when(messageService)
                .createScoreNotice(any(ExamFinishedEvent.class));
        // 不抛出：通知失败不得影响考试交卷
        listener().onExamFinished(new ExamFinishedEvent(11L, 10L, 5L,
                new BigDecimal("80.00"), 8, 10, LocalDateTime.now()));
        verify(messageService).createScoreNotice(any(ExamFinishedEvent.class));
    }
}
