package com.yunsie.common.error;

/**
 * 权限错误码（码段 2xxxx，api-design）。
 * HTTP 层约定：2xxxx 业务异常返回 HTTP 403（见 boot GlobalExceptionHandler）。
 */
public enum PermissionErrorCode implements ErrorCode {

    /** 无操作权限（权限点校验失败） */
    NO_PERMISSION(20001, "无操作权限"),

    /** 越权访问他人数据（DataScope） */
    FORBIDDEN_ACCESS(20002, "越权访问他人数据");

    private final int code;
    private final String message;

    PermissionErrorCode(int code, String message) {
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
