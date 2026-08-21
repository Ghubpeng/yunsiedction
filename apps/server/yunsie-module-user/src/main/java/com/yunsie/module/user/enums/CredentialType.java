package com.yunsie.module.user.enums;

/**
 * 凭证类型（user_credential.credential_type）。
 * MVP 仅密码；短信验证码等后续扩展（不实现供应商，先留类型位）。
 */
public enum CredentialType {

    PASSWORD(1, "密码");

    private final int code;
    private final String desc;

    CredentialType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int code() {
        return code;
    }

    public String desc() {
        return desc;
    }
}
