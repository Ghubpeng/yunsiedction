package com.yunsie.module.question.enums;

/**
 * 题目状态机（question_question.status）。
 * 草稿 → 待审核 → 已发布 → 已下架；待审核 → 驳回 → 草稿（重新编辑）。
 * Stage 2.3B：已发布删除 → 回收站（RECYCLED，历史数据保护）；回收站 → 恢复 → 草稿（重新走审核）。
 * 发布唯一通道 = approve（审核），不可绕过（question-bank 铁律）。
 */
public enum QuestionStatus {

    DRAFT(1, "草稿"),
    PENDING_REVIEW(2, "待审核"),
    REJECTED(3, "驳回"),
    PUBLISHED(4, "已发布"),
    OFFLINE(5, "已下架"),
    RECYCLED(6, "回收站");

    private final int code;
    private final String desc;

    QuestionStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int code() {
        return code;
    }

    public String desc() {
        return desc;
    }

    public static QuestionStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (QuestionStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
