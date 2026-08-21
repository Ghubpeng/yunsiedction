package com.yunsie.module.subject.error;

import com.yunsie.common.error.ErrorCode;

/**
 * subject 域错误码（业务码段 3xxxx，subject 占用 30201-30219）。
 */
public enum SubjectErrorCode implements ErrorCode {

    SUBJECT_CODE_EXISTS(30201, "科目编码在该证书下已存在"),
    SUBJECT_NOT_FOUND(30202, "考试科目不存在"),
    SUBJECT_HAS_NODES(30203, "科目下存在知识节点，禁止删除"),
    SUBJECT_EXISTS_BLOCK_CERT_DELETE(30204, "该证书下存在考试科目，禁止删除证书"),
    VERSION_NOT_FOUND(30205, "知识体系版本不存在"),
    VERSION_NO_EXISTS(30206, "版本号在该证书下已存在"),
    VERSION_HAS_NODES(30207, "版本下存在知识节点，禁止删除"),
    VERSION_ARCHIVED(30208, "归档版本禁止编辑"),
    NODE_NOT_FOUND(30209, "知识节点不存在"),
    NODE_CODE_EXISTS(30210, "节点编码在该版本下已存在"),
    NODE_HAS_CHILDREN(30211, "存在子节点，请先删除子节点"),
    NODE_MOVE_INVALID(30212, "移动目标非法：不能移动到自身、后代或非法层级下"),
    NODE_PARENT_TYPE_INVALID(30213, "父节点类型不符合层级规则：章节根挂载/知识点挂章节/子知识点挂知识点"),
    CURRENT_VERSION_NOT_FOUND(30214, "该证书尚无当前知识体系版本"),
    SUBJECT_HAS_COURSES(30215, "科目下存在关联课程，禁止删除，请先停用"),
    SUBJECT_HAS_EXAMS(30216, "科目下存在关联考试，禁止删除，请先停用"),
    NODE_HAS_COURSES(30217, "章节下存在课程，禁止删除，请先停用"),
    NODE_HAS_QUESTIONS(30218, "知识点已被题目关联，禁止删除，请先停用");

    private final int code;
    private final String message;

    SubjectErrorCode(int code, String message) {
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
