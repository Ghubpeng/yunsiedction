package com.yunsie.common.error;

/**
 * 通用错误码（api-design：错误码集中定义；码段见 {@link ErrorCodeRange}）。
 * 业务域错误码从 30011 起自行分段（sys 用 30011-30019，user 用 30021-30029…），避免与本枚举冲突。
 */
public enum CommonErrorCode implements ErrorCode {

    PARAM_INVALID(30001, "参数校验失败"),
    SYSTEM_ERROR(50001, "系统繁忙，请稍后重试");

    private final int code;
    private final String message;

    CommonErrorCode(int code, String message) {
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
