package com.yunsie.common.exception;

import com.yunsie.common.error.ErrorCode;

import java.io.Serial;

/**
 * 业务异常。由全局异常处理器统一转换为 {@link com.yunsie.common.api.Result}。
 * 禁止吞异常：业务失败必须抛出本异常或记录日志（backend-development）。
 */
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(ErrorCode errorCode) {
        super(errorCode.message());
        this.code = errorCode.code();
    }

    public int getCode() {
        return code;
    }
}
