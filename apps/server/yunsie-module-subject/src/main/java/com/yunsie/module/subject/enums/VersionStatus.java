package com.yunsie.module.subject.enums;

/**
 * 知识体系版本状态（subject_version.status）。
 */
public enum VersionStatus {

    DRAFT(1, "草稿"),
    CURRENT(2, "当前"),
    ARCHIVED(3, "归档");

    private final int code;
    private final String desc;

    VersionStatus(int code, String desc) {
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
