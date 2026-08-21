package com.yunsie.module.notify.service;

import com.yunsie.module.exam.api.ExamFinishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 考试交卷事件消费者（notify）：交卷后异步产生站内成绩通知。
 * - 异步：@Async(notifyExecutor) 不阻塞考试交卷主流程。
 * - 幂等：notify_message uk(user_id,message_type,biz_id,deleted) + insert-catch-DuplicateKey。
 * - 失败隔离：异常仅记录日志，不影响考试；无重试（消息属派生数据，丢失不阻塞业务）。
 * 注：与 learning-profile 域同名监听器并存，显式 bean 名避免组件扫描冲突。
 */
@Slf4j
@Component("notifyExamFinishedEventListener")
@RequiredArgsConstructor
public class ExamFinishedEventListener {

    private final NotifyMessageService messageService;

    @Async("notifyExecutor")
    @EventListener
    public void onExamFinished(ExamFinishedEvent event) {
        if (event == null || event.userId() == null || event.attemptId() == null) {
            return;
        }
        try {
            messageService.createScoreNotice(event);
        } catch (Exception e) {
            log.error("站内成绩通知生成失败(不影响考试主流程): attemptId={}, userId={}",
                    event.attemptId(), event.userId(), e);
        }
    }
}
