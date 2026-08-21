package com.yunsie.module.question.enums;

/**
 * 练习模式（question_practice_record.practice_mode）。
 */
public enum PracticeMode {

    SEQUENCE(1, "顺序练习"),
    CATEGORY(2, "分类练习"),
    KNOWLEDGE_POINT(3, "知识点练习"),
    MISTAKE(4, "错题练习");

    private final int code;
    private final String desc;

    PracticeMode(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int code() {
        return code;
    }

    public String desc() {
        return desc;
    }

    public static PracticeMode of(Integer code) {
        if (code == null) {
            return null;
        }
        for (PracticeMode m : values()) {
            if (m.code == code) {
                return m;
            }
        }
        return null;
    }
}
