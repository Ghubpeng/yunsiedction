package com.yunsie.module.notify.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.exam.api.ExamFinishedEvent;
import com.yunsie.module.exam.api.ExamQueryApi;
import com.yunsie.module.notify.entity.NotifyMessage;
import com.yunsie.module.notify.mapper.NotifyMessageMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站内消息服务单测：本人分页/未读数/已读幂等/越权 30701/删除/成绩通知/提醒/uk 幂等。
 * 注意：BaseMapper.insert 重载歧义 → 一律 ArgumentCaptor 指定类型。
 */
class NotifyMessageServiceTest {

    private final NotifyMessageMapper messageMapper = mock(NotifyMessageMapper.class);
    private final ExamQueryApi examQueryApi = mock(ExamQueryApi.class);

    private NotifyMessageService service() {
        return new NotifyMessageService(messageMapper, examQueryApi);
    }

    private NotifyMessage message(long id, Long userId, int type, Long bizId, int isRead) {
        NotifyMessage m = new NotifyMessage();
        m.setId(id);
        m.setUserId(userId);
        m.setTitle("标题" + id);
        m.setContent("内容" + id);
        m.setMessageType(type);
        m.setBizId(bizId);
        m.setIsRead(isRead);
        m.setCreateTime(LocalDateTime.of(2026, 8, 20, 10, 0));
        return m;
    }

    @Test
    void page_returnsOwnMessages() {
        Page<NotifyMessage> p = new Page<>(1, 10);
        p.setRecords(List.of(message(1L, 10L, 1, 100L, 0)));
        p.setTotal(1);
        when(messageMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(p);

        var result = service().page(10L, 1, 10, null);

        assertEquals(1, result.total());
        assertEquals(1, result.list().size());
        assertEquals("标题1", result.list().get(0).title());
    }

    @Test
    void unreadCount_queriesOwnUnread() {
        when(messageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
        assertEquals(3L, service().unreadCount(10L));
    }

    @Test
    void markRead_ownMessage_reads() {
        NotifyMessage m = message(1L, 10L, 1, 100L, 0);
        when(messageMapper.selectById(1L)).thenReturn(m);
        service().markRead(10L, 1L);
        verify(messageMapper).updateById(m);
        assertEquals(1, m.getIsRead());
        // 重复标记幂等：不再 update
        service().markRead(10L, 1L);
        verify(messageMapper, times(1)).updateById(any(NotifyMessage.class));
    }

    @Test
    void markRead_otherUsersMessage_30701() {
        when(messageMapper.selectById(1L)).thenReturn(message(1L, 99L, 1, 100L, 0));
        BizException e = assertThrows(BizException.class, () -> service().markRead(10L, 1L));
        assertEquals(30701, e.getCode());
        verify(messageMapper, never()).updateById(any(NotifyMessage.class));
    }

    @Test
    void delete_otherUsersMessage_30701() {
        when(messageMapper.selectById(1L)).thenReturn(message(1L, 99L, 1, 100L, 0));
        BizException e = assertThrows(BizException.class, () -> service().delete(10L, 1L));
        assertEquals(30701, e.getCode());
        verify(messageMapper, never()).deleteById(anyLong());
    }

    @Test
    void createScoreNotice_insertsWithAttemptBizId() {
        when(examQueryApi.findExam(5L)).thenReturn(
                new ExamQueryApi.ExamView(5L, 9L, "E2E Exam", new BigDecimal("60"), 2));
        service().createScoreNotice(new ExamFinishedEvent(11L, 10L, 5L,
                new BigDecimal("80.00"), 8, 10, LocalDateTime.now()));

        ArgumentCaptor<NotifyMessage> captor = ArgumentCaptor.forClass(NotifyMessage.class);
        verify(messageMapper).insert(captor.capture());
        NotifyMessage m = captor.getValue();
        assertEquals(10L, m.getUserId());
        assertEquals(1, m.getMessageType());
        assertEquals(11L, m.getBizId());
        assertEquals(0, m.getIsRead());
        assertEquals("考试成绩通知", m.getTitle());
        org.junit.jupiter.api.Assertions.assertTrue(m.getContent().contains("E2E Exam"));
        org.junit.jupiter.api.Assertions.assertTrue(m.getContent().contains("80.00"));
    }

    @Test
    void createScoreNotice_duplicate_skipped() {
        when(examQueryApi.findExam(5L)).thenReturn(
                new ExamQueryApi.ExamView(5L, 9L, "E2E Exam", new BigDecimal("60"), 2));
        ArgumentCaptor<NotifyMessage> captor = ArgumentCaptor.forClass(NotifyMessage.class);
        doThrow(new DuplicateKeyException("dup")).when(messageMapper).insert(captor.capture());
        // 不抛异常（幂等跳过）
        service().createScoreNotice(new ExamFinishedEvent(11L, 10L, 5L,
                new BigDecimal("80.00"), 8, 10, LocalDateTime.now()));
        verify(messageMapper).insert(any(NotifyMessage.class));
    }

    @Test
    void createExamReminder_insertsWithExamBizId() {
        service().createExamReminder(10L, new ExamQueryApi.OpeningExamView(7L, 9L, "E2E Exam",
                LocalDateTime.of(2026, 8, 21, 9, 0)));
        ArgumentCaptor<NotifyMessage> captor = ArgumentCaptor.forClass(NotifyMessage.class);
        verify(messageMapper).insert(captor.capture());
        NotifyMessage m = captor.getValue();
        assertEquals(2, m.getMessageType());
        assertEquals(7L, m.getBizId());
        org.junit.jupiter.api.Assertions.assertTrue(m.getContent().contains("2026-08-21 09:00"));
    }

    @Test
    void createExamReminder_duplicate_skipped() {
        ArgumentCaptor<NotifyMessage> captor = ArgumentCaptor.forClass(NotifyMessage.class);
        doThrow(new DuplicateKeyException("dup")).when(messageMapper).insert(captor.capture());
        service().createExamReminder(10L, new ExamQueryApi.OpeningExamView(7L, 9L, "E2E Exam",
                LocalDateTime.of(2026, 8, 21, 9, 0)));
        verify(messageMapper).insert(any(NotifyMessage.class));
    }
}
