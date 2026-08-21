package com.yunsie.module.course.error;

import com.yunsie.common.error.ErrorCode;

/**
 * course 域错误码（业务码段 3xxxx，course 占用 30501-30519）。
 */
public enum CourseErrorCode implements ErrorCode {

    COURSE_NOT_FOUND(30501, "课程不存在"),
    COURSE_STATUS_INVALID_ACTION(30502, "课程状态非法，不允许该操作"),
    COURSE_PUBLISHED_NOT_EDITABLE(30503, "已发布课程禁止编辑"),
    COURSE_PUBLISHED_NOT_DELETABLE(30504, "已发布课程禁止删除，请先下架"),
    COURSE_PUBLISH_REQUIRE_CONTENT(30505, "发布前必须存在至少 1 个启用章节且至少 1 个启用小节"),
    COURSE_FORBIDDEN(30506, "无权管理该课程（教师数据范围）"),
    CHAPTER_NOT_FOUND(30507, "章节不存在"),
    CHAPTER_HAS_LESSONS(30508, "章节下存在小节，请先删除小节"),
    LESSON_NOT_FOUND(30509, "小节不存在"),
    LESSON_NOT_PLAYABLE(30510, "小节不可播放：课程未发布或小节已禁用"),
    VIDEO_UPLOAD_FAILED(30511, "视频上传失败"),
    VIDEO_NOT_UPLOADED(30512, "小节尚未上传视频"),
    NODE_ASSOC_INVALID(30513, "知识点关联非法：节点不存在/未启用/非知识点类型/不属于该课程证书"),
    PROGRESS_INVALID(30514, "进度上报非法"),
    COURSE_NOT_PUBLISHED(30515, "课程未发布"),
    CHAPTER_PRACTICE_INVALID(30516, "章节练习上报非法：章节不存在、课程未发布或题数不正确");

    private final int code;
    private final String message;

    CourseErrorCode(int code, String message) {
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
