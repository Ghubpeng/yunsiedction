package com.yunsie.module.exam.enums;

/**
 * 考试实例状态（exam_attempt.status）。
 * 合法迁移：未开始 → 进行中 → 已交卷（手动交卷或超时自动交卷）；其余迁移一律拒绝。
 */
public enum AttemptStatus {

    NOT_STARTED(1, "未开始"),
    IN_PROGRESS(2, "进行中"),
    SUBMITTED(3, "已交卷");

    private final int code;
    private final String desc;

    AttemptStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int code() {
        return code;
    }

    public String desc() {
        return desc;
    }

    public static AttemptStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (AttemptStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
