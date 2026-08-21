package com.yunsie.module.notify.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 站内消息表（notify_message）：本人数据。
 * uk(user_id, message_type, biz_id, deleted) 天然幂等去重：
 * 同一事件（attemptId/examId）重复投递不会产生重复消息（CONFLICTS #21）。
 */
@Getter
@Setter
@TableName("notify_message")
public class NotifyMessage extends BaseEntity {

    /** 接收用户ID(user域) */
    private Long userId;

    /** 消息标题 */
    private String title;

    /** 消息内容 */
    private String content;

    /** 消息类型: 1-考试成绩通知 2-考试开考提醒 3-系统消息(预留) */
    private Integer messageType;

    /** 业务对象ID(成绩通知=attemptId; 开考提醒=examId; 幂等去重依据) */
    private Long bizId;

    /** 已读: 0-未读 1-已读 */
    private Integer isRead;

    /** 阅读时间 */
    private LocalDateTime readTime;
}
