package com.yunsie.module.course.enums;

/**
 * 课程状态机（course_course.status）。
 * 草稿 → 已发布 → 已下架；已下架 → 草稿。已发布禁止编辑/删除。
 * 发布前置：至少 1 个启用章节 且 至少 1 个启用小节。
 */
public enum CourseStatus {

    DRAFT(1, "草稿"),
    PUBLISHED(2, "已发布"),
    OFFLINE(3, "已下架");

    private final int code;
    private final String desc;

    CourseStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int code() {
        return code;
    }

    public String desc() {
        return desc;
    }

    public static CourseStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (CourseStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
