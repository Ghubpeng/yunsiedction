package com.yunsie.module.learning.profile.enums;

/**
 * 学习事件类型（learn_event_dedup.event_type）。
 */
public enum EventType {

    /** 考试交卷完成（exam 域 ExamFinishedEvent） */
    EXAM_FINISHED("EXAM_FINISHED");

    private final String code;

    EventType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
