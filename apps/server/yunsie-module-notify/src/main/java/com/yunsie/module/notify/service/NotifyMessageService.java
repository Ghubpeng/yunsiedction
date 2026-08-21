package com.yunsie.module.notify.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunsie.common.api.PageResult;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.exam.api.ExamFinishedEvent;
import com.yunsie.module.exam.api.ExamQueryApi;
import com.yunsie.module.notify.entity.NotifyMessage;
import com.yunsie.module.notify.enums.MessageType;
import com.yunsie.module.notify.error.NotifyErrorCode;
import com.yunsie.module.notify.mapper.NotifyMessageMapper;
import com.yunsie.module.notify.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 站内消息服务（本人数据，严格 userId 隔离）。
 * 写入路径（成绩通知/开考提醒）以 uk(user_id,message_type,biz_id,deleted) 幂等：
 * insert 捕获 DuplicateKeyException 跳过（CONFLICTS #21）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyMessageService {

    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final NotifyMessageMapper messageMapper;
    private final ExamQueryApi examQueryApi;

    /** 本人消息分页（unreadOnly=true 仅未读），按时间倒序 */
    public PageResult<MessageVO> page(Long userId, int pageNum, int pageSize, Boolean unreadOnly) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        LambdaQueryWrapper<NotifyMessage> wrapper = new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, userId);
        if (Boolean.TRUE.equals(unreadOnly)) {
            wrapper.eq(NotifyMessage::getIsRead, 0);
        }
        wrapper.orderByDesc(NotifyMessage::getId);
        Page<NotifyMessage> page = messageMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        List<MessageVO> list = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(list, page.getTotal());
    }

    public long unreadCount(Long userId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, userId)
                .eq(NotifyMessage::getIsRead, 0));
    }

    /** 标记已读（本人消息；重复标记幂等） */
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long id) {
        NotifyMessage message = requireOwn(userId, id);
        if (message.getIsRead() != null && message.getIsRead() == 1) {
            return;
        }
        message.setIsRead(1);
        message.setReadTime(LocalDateTime.now());
        messageMapper.updateById(message);
    }

    /** 全部标记已读（仅本人未读） */
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        messageMapper.update(null, new UpdateWrapper<NotifyMessage>()
                .eq("user_id", userId)
                .eq("is_read", 0)
                .set("is_read", 1)
                .set("read_time", LocalDateTime.now()));
    }

    /** 删除本人消息（逻辑删除） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        requireOwn(userId, id);
        messageMapper.deleteById(id);
    }

    // ---------- 事件/扫描写入（uk 幂等） ----------

    /** 成绩通知：消费 ExamFinishedEvent；biz_id=attemptId 去重 */
    public void createScoreNotice(ExamFinishedEvent event) {
        ExamQueryApi.ExamView exam = examQueryApi.findExam(event.examId());
        String name = exam == null ? "考试#" + event.examId() : exam.name();
        insertQuietly(event.userId(), MessageType.SCORE_NOTICE.desc(),
                String.format("《%s》成绩已出：得分 %s 分（答对 %d/%d 题）。",
                        name,
                        event.score() == null ? "-" : event.score().toPlainString(),
                        event.correctCount() == null ? 0 : event.correctCount(),
                        event.questionCount() == null ? 0 : event.questionCount()),
                MessageType.SCORE_NOTICE.code(), event.attemptId());
    }

    /** 开考提醒：@Scheduled 扫描写入；biz_id=examId 去重（每用户每考试仅一次） */
    public void createExamReminder(Long userId, ExamQueryApi.OpeningExamView exam) {
        insertQuietly(userId, MessageType.EXAM_REMINDER.desc(),
                String.format("《%s》将于 %s 开放考试，请做好准备。",
                        exam.name(), exam.validFrom() == null ? "" : exam.validFrom().format(MINUTE_FMT)),
                MessageType.EXAM_REMINDER.code(), exam.id());
    }

    private void insertQuietly(Long userId, String title, String content, int type, Long bizId) {
        if (userId == null || bizId == null) {
            return;
        }
        NotifyMessage message = new NotifyMessage();
        message.setUserId(userId);
        message.setTitle(title);
        message.setContent(content);
        message.setMessageType(type);
        message.setBizId(bizId);
        message.setIsRead(0);
        try {
            messageMapper.insert(message);
        } catch (DuplicateKeyException e) {
            log.debug("重复消息跳过（uk 幂等）: userId={}, type={}, bizId={}", userId, type, bizId);
        }
    }

    private NotifyMessage requireOwn(Long userId, Long id) {
        NotifyMessage message = messageMapper.selectById(id);
        if (message == null || !message.getUserId().equals(userId)) {
            throw new BizException(NotifyErrorCode.MESSAGE_NOT_FOUND);
        }
        return message;
    }

    private MessageVO toVO(NotifyMessage m) {
        return new MessageVO(m.getId(), m.getTitle(), m.getContent(), m.getMessageType(), m.getBizId(),
                m.getIsRead(), m.getReadTime(), m.getCreateTime());
    }
}
