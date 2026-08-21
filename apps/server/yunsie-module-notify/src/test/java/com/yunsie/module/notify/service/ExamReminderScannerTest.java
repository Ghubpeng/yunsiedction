package com.yunsie.module.notify.service;

import com.yunsie.module.exam.api.ExamQueryApi;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 开考提醒扫描单测：窗口内考试 × 历史学员定向；单条失败不影响其余。
 */
class ExamReminderScannerTest {

    private final ExamQueryApi examQueryApi = mock(ExamQueryApi.class);
    private final NotifyMessageService messageService = mock(NotifyMessageService.class);

    private ExamReminderScanner scanner() {
        ExamReminderScanner scanner = new ExamReminderScanner(examQueryApi, messageService);
        ReflectionTestUtils.setField(scanner, "lookaheadHours", 24L);
        return scanner;
    }

    @Test
    void scan_createsReminderPerLearnerPerExam() {
        var exam1 = new ExamQueryApi.OpeningExamView(7L, 9L, "Exam A",
                LocalDateTime.now().plusHours(2));
        var exam2 = new ExamQueryApi.OpeningExamView(8L, 9L, "Exam B",
                LocalDateTime.now().plusHours(5));
        when(examQueryApi.listExamsOpeningSoon(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(exam1, exam2));
        when(examQueryApi.listLearnerIdsByCertificate(9L)).thenReturn(List.of(10L, 11L));

        scanner().scan();

        verify(messageService).createExamReminder(10L, exam1);
        verify(messageService).createExamReminder(11L, exam1);
        verify(messageService).createExamReminder(10L, exam2);
        verify(messageService).createExamReminder(11L, exam2);
    }

    @Test
    void scan_noExams_noMessages() {
        when(examQueryApi.listExamsOpeningSoon(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        scanner().scan();
        verify(messageService, times(0)).createExamReminder(anyLong(), any());
    }

    @Test
    void scan_singleFailure_doesNotAbortOthers() {
        var exam1 = new ExamQueryApi.OpeningExamView(7L, 9L, "Exam A", LocalDateTime.now().plusHours(1));
        when(examQueryApi.listExamsOpeningSoon(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(exam1));
        when(examQueryApi.listLearnerIdsByCertificate(9L)).thenReturn(List.of(10L, 11L));
        doThrow(new RuntimeException("boom")).when(messageService).createExamReminder(10L, exam1);

        scanner().scan();

        verify(messageService).createExamReminder(11L, exam1); // 后续学员不受影响
    }
}
