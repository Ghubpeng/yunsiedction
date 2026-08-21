package com.yunsie.module.learning.profile.enums;

/**
 * 掌握度更新来源（learn_mastery.last_trigger_source，可追溯：learning-profile 强制掌握度变化可追溯）。
 */
public enum TriggerSource {

    /** 练习作答（重算聚合自 question 练习记录） */
    PRACTICE("PRACTICE"),

    /** 重算兜底（管理员 recalc / 读时同步重建） */
    RECALC("RECALC");

    private final String code;

    TriggerSource(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
