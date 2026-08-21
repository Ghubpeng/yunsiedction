package com.yunsie.module.user.error;

import com.yunsie.common.error.ErrorCode;

/**
 * user 域错误码（业务码段 3xxxx，user 占用 30021-30029）。
 */
public enum UserErrorCode implements ErrorCode {

    USERNAME_EXISTS(30021, "账号已存在"),
    MOBILE_EXISTS(30022, "手机号已被占用"),
    USER_NOT_FOUND(30023, "用户不存在"),
    CREDENTIAL_NOT_FOUND(30024, "登录凭证不存在"),
    USER_STATUS_INVALID(30025, "用户状态非法");

    private final int code;
    private final String message;

    UserErrorCode(int code, String message) {
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
