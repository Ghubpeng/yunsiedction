package com.yunsie.module.notify.error;

import com.yunsie.common.error.ErrorCode;

/**
 * notify 域错误码（业务码段 3xxxx，notify 占用 30701-30719）。
 */
public enum NotifyErrorCode implements ErrorCode {

    /** 消息不存在或非本人消息（对外统一语义，避免越权探测） */
    MESSAGE_NOT_FOUND(30701, "消息不存在");

    private final int code;
    private final String message;

    NotifyErrorCode(int code, String message) {
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
