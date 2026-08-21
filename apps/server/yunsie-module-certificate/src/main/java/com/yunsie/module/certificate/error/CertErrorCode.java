package com.yunsie.module.certificate.error;

import com.yunsie.common.error.ErrorCode;

/**
 * certificate 域错误码（业务码段 3xxxx，certificate 占用 30101-30119）。
 */
public enum CertErrorCode implements ErrorCode {

    CATEGORY_CODE_EXISTS(30101, "分类编码已存在"),
    CATEGORY_NOT_FOUND(30102, "分类不存在"),
    CATEGORY_HAS_CHILDREN(30103, "存在子分类，请先删除子分类"),
    CATEGORY_HAS_CERTIFICATES(30104, "分类下存在证书，禁止删除"),
    CATEGORY_MOVE_INVALID(30105, "移动目标非法：不能移动到自身或其子节点下"),
    CERT_CODE_EXISTS(30106, "证书编码已存在"),
    CERT_NOT_FOUND(30107, "证书不存在"),
    CERT_CATEGORY_NOT_FOUND(30108, "所属分类不存在");

    private final int code;
    private final String message;

    CertErrorCode(int code, String message) {
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
