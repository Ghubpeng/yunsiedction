package com.yunsie.module.notify.service;

import com.yunsie.module.exam.api.ExamQueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试开考提醒扫描（CONFLICTS #21）：
 * 周期性扫描 valid_from 落入 [now, now+lookahead] 的已发布考试，
 * 定向该证书有已交卷考试历史的学员发送站内提醒（每人每考试仅一次，uk 幂等）。
 * 提前窗口/扫描间隔配置化（application.yml yunsie.notify.exam-reminder.*）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamReminderScanner {

    private final ExamQueryApi examQueryApi;
    private final NotifyMessageService messageService;

    @Value("${yunsie.notify.exam-reminder.lookahead-hours:24}")
    private long lookaheadHours;

    @Scheduled(fixedDelayString = "${yunsie.notify.exam-reminder.scan-delay-ms:60000}",
            initialDelayString = "30000")
    public void scan() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = now.plusHours(lookaheadHours);
        List<ExamQueryApi.OpeningExamView> exams = examQueryApi.listExamsOpeningSoon(now, until);
        for (ExamQueryApi.OpeningExamView exam : exams) {
            List<Long> learnerIds = examQueryApi.listLearnerIdsByCertificate(exam.certificateId());
            for (Long userId : learnerIds) {
                try {
                    messageService.createExamReminder(userId, exam);
                } catch (Exception e) {
                    // 单条失败不影响其余（uk 冲突已由 insertQuietly 吞掉；此处兜底其他异常）
                    log.warn("开考提醒生成失败: examId={}, userId={}", exam.id(), userId, e);
                }
            }
        }
    }
}
