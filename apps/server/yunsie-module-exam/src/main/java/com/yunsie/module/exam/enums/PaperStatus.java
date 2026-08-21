package com.yunsie.module.exam.enums;

/**
 * 试卷快照状态（exam_paper.status）。
 * 重新组卷 = 旧有效卷作废 + 新卷生效；考试发布后不再产生新卷。
 */
public enum PaperStatus {

    VALID(1, "有效"),
    VOID(2, "作废");

    private final int code;
    private final String desc;

    PaperStatus(int code, String desc) {
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
