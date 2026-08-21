package com.yunsie.common.error;

/**
 * 认证错误码（码段 1xxxx，api-design）。
 */
public enum AuthErrorCode implements ErrorCode {

    /** 未登录 / 未携带有效凭证 */
    UNAUTHORIZED(10001, "未登录或登录已过期"),

    /** token 无效、被篡改或已过期 */
    TOKEN_INVALID(10002, "登录凭证无效或已过期"),

    /** 账号不存在与密码错误统一返回，防止账号枚举 */
    BAD_CREDENTIALS(10003, "账号或密码错误"),

    /** 账号被禁用/锁定 */
    ACCOUNT_DISABLED(10004, "账号已被禁用，请联系管理员"),

    /** Demo 账号未就绪（未执行 scripts/demo.ps1 播种） */
    DEMO_NOT_READY(10005, "演示账号未就绪，请先执行 demo 播种脚本");

    private final int code;
    private final String message;

    AuthErrorCode(int code, String message) {
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
