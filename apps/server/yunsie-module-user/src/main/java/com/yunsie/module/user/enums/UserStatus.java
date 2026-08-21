package com.yunsie.module.user.enums;

/**
 * 用户状态（user_account.status）。
 */
public enum UserStatus {

    DISABLED(0, "禁用"),
    NORMAL(1, "正常"),
    LOCKED(2, "锁定");

    private final int code;
    private final String desc;

    UserStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int code() {
        return code;
    }

    public String desc() {
        return desc;
    }

    public static UserStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (UserStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
