package com.yunsie.module.course.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 课程小节表（course_lesson）：视频原文件存 MinIO（不转码）。
 */
@Getter
@Setter
@TableName("course_lesson")
public class CourseLesson extends BaseEntity {

    /** 所属章节ID */
    private Long chapterId;

    /** 小节标题 */
    private String title;

    /** 视频对象键(MinIO; NULL=未上传) */
    private String videoKey;

    /** 视频时长(秒, 管理员填写; 进度截断依据) */
    private Integer durationSeconds;

    /** 排序(越小越靠前) */
    private Integer sort;

    /** 状态: 1-启用 0-禁用(已发布课程中禁用对学员隐藏) */
    private Integer status;
}
