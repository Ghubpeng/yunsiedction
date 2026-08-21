package com.yunsie.module.sys.error;

import com.yunsie.common.error.ErrorCode;

/**
 * sys 域错误码（业务码段 3xxxx，sys 占用 30011-30019）。
 */
public enum SysErrorCode implements ErrorCode {

    ROLE_CODE_EXISTS(30011, "角色编码已存在"),
    ROLE_NOT_FOUND(30012, "角色不存在"),
    PERMISSION_CODE_EXISTS(30013, "权限编码已存在"),
    PERMISSION_NOT_FOUND(30014, "权限不存在"),
    ROLE_PROTECTED(30015, "系统内置角色禁止删除"),
    PERMISSION_HAS_CHILDREN(30016, "存在子权限，请先删除子权限"),
    DATA_SCOPE_INVALID(30017, "数据范围配置无效：指定范围必须提供范围明细");

    private final int code;
    private final String message;

    SysErrorCode(int code, String message) {
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
