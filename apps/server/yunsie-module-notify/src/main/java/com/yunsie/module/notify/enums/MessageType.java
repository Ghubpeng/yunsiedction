package com.yunsie.module.notify.enums;

/**
 * 站内消息类型（notify_message.message_type）。
 */
public enum MessageType {

    /** 考试成绩通知（消费 ExamFinishedEvent；biz_id=attemptId） */
    SCORE_NOTICE(1, "考试成绩通知"),

    /** 考试开考提醒（@Scheduled 扫描；biz_id=examId） */
    EXAM_REMINDER(2, "考试开考提醒"),

    /** 系统消息（预留，MVP 不实现） */
    SYSTEM(3, "系统消息");

    private final int code;
    private final String desc;

    MessageType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int code() {
        return code;
    }

    public String desc() {
        return desc;
    }
}
