package com.yunsie.module.learning.profile.error;

import com.yunsie.common.error.ErrorCode;

/**
 * learning-profile 域错误码（业务码段 3xxxx，learning-profile 占用 30601-30619）。
 */
public enum LearnErrorCode implements ErrorCode {

    PROFILE_NOT_FOUND(30601, "学习档案不存在"),
    LEARN_FORBIDDEN(30602, "无权查看该学员档案（教师数据范围）"),
    STUDENT_NOT_FOUND(30603, "学员不存在"),
    CALENDAR_MONTH_INVALID(30604, "月份参数非法（格式 yyyy-MM）"),
    EXAM_NOT_FOUND(30605, "考试不存在（无法预测）"),
    WEAKNESS_LIMIT_INVALID(30606, "薄弱点条数非法（1-50）"),
    GOAL_CERT_INVALID(30607, "考试目标证书不存在或未启用"),
    GOAL_SUBJECT_INVALID(30608, "考试科目不存在、未启用或不属于该证书"),
    GOAL_NO_CURRENT_VERSION(30609, "该证书尚未设置当前版本，无法建立考试目标");

    private final int code;
    private final String message;

    LearnErrorCode(int code, String message) {
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
