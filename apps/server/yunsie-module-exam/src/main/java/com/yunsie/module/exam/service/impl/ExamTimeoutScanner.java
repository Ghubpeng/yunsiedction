package com.yunsie.module.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.exam.entity.ExamAttempt;
import com.yunsie.module.exam.enums.AttemptStatus;
import com.yunsie.module.exam.mapper.ExamAttemptMapper;
import com.yunsie.module.exam.service.AttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时兜底扫描（第二层保险；第一层为懒结算）。
 * 每 30 秒扫描 进行中且已过期的 attempt，调用统一自动交卷路径（与手动交卷共用条件更新，不重复计分）。
 * 无 MQ/Redis，Spring 内置调度。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamTimeoutScanner {

    private static final int BATCH_SIZE = 100;

    private final ExamAttemptMapper attemptMapper;
    private final AttemptService attemptService;

    @Scheduled(fixedDelay = 30_000)
    public void sweepExpiredAttempts() {
        List<ExamAttempt> expired = attemptMapper.selectList(new LambdaQueryWrapper<ExamAttempt>()
                .eq(ExamAttempt::getStatus, AttemptStatus.IN_PROGRESS.code())
                .le(ExamAttempt::getExpiredAt, LocalDateTime.now())
                .last("LIMIT " + BATCH_SIZE));
        for (ExamAttempt attempt : expired) {
            try {
                attemptService.autoSubmit(attempt.getId());
            } catch (Exception e) {
                log.error("auto-submit attempt failed, attemptId={}", attempt.getId(), e);
            }
        }
    }
}
