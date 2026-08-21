package com.yunsie.module.learning.profile.service;

import com.yunsie.module.exam.api.ExamFinishedEvent;
import com.yunsie.module.learning.profile.entity.LearnEventDedup;
import com.yunsie.module.learning.profile.enums.EventType;
import com.yunsie.module.learning.profile.mapper.LearnEventDedupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 考试交卷事件消费者（ExamFinishedEvent 的首个消费者）。
 * - 异步：@Async(profileSyncExecutor) 不阻塞考试交卷主流程（learning-profile §4）。
 * - 幂等：learn_event_dedup uk(dedup_key) 去重，重复事件直接跳过，不重复累计。
 * - 失败隔离：处理失败仅记录日志，不影响考试；兜底=读时同步 + 管理员 recalc（CONFLICTS #18）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamFinishedEventListener {

    private final LearnEventDedupMapper dedupMapper;
    private final ProfileSyncService syncService;

    @Async("profileSyncExecutor")
    @EventListener
    public void onExamFinished(ExamFinishedEvent event) {
        if (event == null || event.userId() == null || event.attemptId() == null) {
            return;
        }
        String dedupKey = "exam:" + event.attemptId();
        LearnEventDedup dedup = new LearnEventDedup();
        dedup.setDedupKey(dedupKey);
        dedup.setEventType(EventType.EXAM_FINISHED.code());
        dedup.setProcessedAt(LocalDateTime.now());
        try {
            dedupMapper.insert(dedup);
        } catch (DuplicateKeyException e) {
            // 已处理过（重复投递/重试）：幂等跳过
            log.debug("学习事件重复投递，跳过: {}", dedupKey);
            return;
        }
        try {
            syncService.sync(event.userId());
        } catch (Exception e) {
            // 崩溃窗口/瞬时失败：不重试（避免复杂化），依赖读时同步与 recalc 兜底自愈
            log.error("学习档案事件处理失败(不影响考试主流程): attemptId={}, userId={}",
                    event.attemptId(), event.userId(), e);
        }
    }
}
