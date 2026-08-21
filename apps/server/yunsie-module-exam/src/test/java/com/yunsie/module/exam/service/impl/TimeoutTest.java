package com.yunsie.module.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.exam.entity.ExamAttempt;
import com.yunsie.module.exam.enums.AttemptStatus;
import com.yunsie.module.exam.mapper.ExamAttemptMapper;
import com.yunsie.module.exam.service.AttemptService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 超时扫描单元测试：仅扫描 进行中+已过期；未过期不触发；扫描调用统一自动交卷。
 */
class TimeoutTest {

    @Test
    void scanner_onlySubmitsExpiredInProgress() {
        ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
        ExamAttempt expired = new ExamAttempt();
        expired.setId(1L);
        expired.setStatus(AttemptStatus.IN_PROGRESS.code());
        expired.setExpiredAt(LocalDateTime.now().minusMinutes(1));
        when(attemptMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(expired));
        AttemptService attemptService = mock(AttemptService.class);

        new ExamTimeoutScanner(attemptMapper, attemptService).sweepExpiredAttempts();

        verify(attemptService).autoSubmit(1L);
    }

    @Test
    void autoSubmit_notExpired_noFinalize() {
        ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
        ExamAttempt inProgress = new ExamAttempt();
        inProgress.setId(2L);
        inProgress.setStatus(AttemptStatus.IN_PROGRESS.code());
        inProgress.setExpiredAt(LocalDateTime.now().plusHours(1));
        when(attemptMapper.selectById(2L)).thenReturn(inProgress);

        new AttemptServiceImpl(mock(com.yunsie.module.exam.mapper.ExamMapper.class),
                mock(com.yunsie.module.exam.mapper.ExamPaperMapper.class),
                mock(com.yunsie.module.exam.mapper.ExamPaperQuestionMapper.class),
                mock(com.yunsie.module.exam.mapper.ExamPaperOptionMapper.class),
                attemptMapper,
                mock(com.yunsie.module.exam.mapper.ExamAnswerMapper.class),
                mock(com.yunsie.module.question.api.QuestionQueryApi.class),
                mock(org.springframework.context.ApplicationEventPublisher.class)).autoSubmit(2L);

        verify(attemptMapper, never()).update(any(), any());
    }
}
