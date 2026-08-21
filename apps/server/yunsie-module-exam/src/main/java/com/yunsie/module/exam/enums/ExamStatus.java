package com.yunsie.module.exam.enums;

/**
 * 考试定义状态（exam_exam.status）。
 * 草稿 → 已发布 → 已下架；已下架 → 草稿（重新编辑/组卷后再次发布）。
 * 已发布：禁止编辑/组卷/删除。
 */
public enum ExamStatus {

    DRAFT(1, "草稿"),
    PUBLISHED(2, "已发布"),
    OFFLINE(3, "已下架");

    private final int code;
    private final String desc;

    ExamStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int code() {
        return code;
    }

    public String desc() {
        return desc;
    }

    public static ExamStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ExamStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
