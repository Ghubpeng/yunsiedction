package com.yunsie.module.exam.error;

import com.yunsie.common.error.ErrorCode;

/**
 * exam 域错误码（业务码段 3xxxx，exam 占用 30401-30419）。
 */
public enum ExamErrorCode implements ErrorCode {

    EXAM_NOT_FOUND(30401, "考试不存在"),
    EXAM_STATUS_INVALID_ACTION(30402, "考试状态非法，不允许该操作"),
    EXAM_PUBLISHED_NOT_EDITABLE(30403, "已发布考试禁止编辑"),
    EXAM_PUBLISHED_NOT_ASSEMBLE(30404, "已发布考试禁止组卷"),
    EXAM_PUBLISHED_NOT_DELETABLE(30405, "已发布考试禁止删除，请先下架"),
    EXAM_HAS_ATTEMPTS(30406, "该考试已存在考试记录，禁止删除（保护历史成绩）"),
    PAPER_NOT_FOUND(30407, "无有效试卷，请先组卷"),
    CANDIDATES_INSUFFICIENT(30408, "候选题不足，无法组卷"),
    ASSEMBLE_RULE_INVALID(30409, "组卷规则非法"),
    EXAM_NOT_AVAILABLE(30410, "考试不可参加（未发布或不在开放时间内）"),
    ATTEMPT_NOT_FOUND(30411, "考试记录不存在"),
    ATTEMPT_FORBIDDEN(30412, "无权访问该考试记录"),
    ATTEMPT_STATUS_INVALID(30413, "考试状态非法，不允许该操作"),
    EXAM_EXPIRED(30414, "考试已超时，请交卷"),
    EXAM_ALREADY_SUBMITTED(30415, "考试已交卷"),
    SAVE_ANSWER_FAILED(30416, "保存答案失败"),
    PAPER_IMMUTABLE(30417, "试卷快照不可变"),
    PAPER_QUESTION_NOT_FOUND(30418, "试卷题目不存在"),
    EXAMS_INSUFFICIENT_NEED(30419, "候选题不足：需要 %d 题，仅 %d 题可用");

    private final int code;
    private final String message;

    ExamErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
