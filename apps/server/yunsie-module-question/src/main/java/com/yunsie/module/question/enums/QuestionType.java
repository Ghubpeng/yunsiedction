package com.yunsie.module.question.enums;

/**
 * 题型（question_question.question_type）。
 * 判分策略集中在 GradingService，禁止 Controller 手写题型 if/else；
 * 未来扩展题型=新增枚举值+判分策略（未实现题型判分默认拒绝，避免静默误判）。
 */
public enum QuestionType {

    SINGLE_CHOICE(1, "单选"),
    MULTIPLE_CHOICE(2, "多选"),
    TRUE_FALSE(3, "判断");

    private final int code;
    private final String desc;

    QuestionType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int code() {
        return code;
    }

    public String desc() {
        return desc;
    }

    public static QuestionType of(Integer code) {
        if (code == null) {
            return null;
        }
        for (QuestionType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return null;
    }
}
