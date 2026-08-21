package com.yunsie.module.user.enums;

/**
 * 用户类型（user_account.user_type）。
 * 管理员为平台内部账号；其能力完全由 RBAC 角色决定（类型仅是标签，不做权限判断）。
 */
public enum UserType {

    STUDENT(1, "学员"),
    TEACHER(2, "教师"),
    ADMIN(3, "管理员");

    private final int code;
    private final String desc;

    UserType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int code() {
        return code;
    }

    public String desc() {
        return desc;
    }

    public static UserType of(Integer code) {
        if (code == null) {
            return null;
        }
        for (UserType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return null;
    }
}
