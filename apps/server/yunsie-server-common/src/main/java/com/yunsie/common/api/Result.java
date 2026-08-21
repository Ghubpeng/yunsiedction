package com.yunsie.common.api;

import com.yunsie.common.web.TraceIdHolder;

import java.io.Serializable;

/**
 * 统一响应体（api-design：{@code {code, message, data, traceId, timestamp}}）。
 *
 * <p>{@code code = 0} 表示成功；非 0 为业务错误，码段见 {@link com.yunsie.common.error.ErrorCodeRange}。
 * 所有 Controller 必须返回本结构，禁止返回裸对象。</p>
 *
 * <p>traceId 自动从 {@link TraceIdHolder} 获取（由 boot TraceIdFilter 注入），贯穿请求与响应。</p>
 *
 * @param <T> 业务数据类型
 */
public record Result<T>(int code, String message, T data, String traceId, long timestamp) implements Serializable {

    public static final int SUCCESS_CODE = 0;

    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, "ok", data, TraceIdHolder.get(), System.currentTimeMillis());
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, TraceIdHolder.get(), System.currentTimeMillis());
    }

    public static <T> Result<T> fail(com.yunsie.common.error.ErrorCode errorCode) {
        return fail(errorCode.code(), errorCode.message());
    }

    public boolean isSuccess() {
        return code == SUCCESS_CODE;
    }
}
