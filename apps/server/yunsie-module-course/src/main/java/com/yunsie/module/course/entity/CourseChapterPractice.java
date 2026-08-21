package com.yunsie.module.course.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 章节练习完成记录表（course_chapter_practice，Stage 2.4）。
 * 章节「掌握」= 视频全部完成 + 本表存在记录；记录最近一次得分/正确数/题数。
 */
@Getter
@Setter
@TableName("course_chapter_practice")
public class CourseChapterPractice extends BaseEntity {

    /** 用户ID(user域) */
    private Long userId;

    /** 课程章节ID(course_chapter) */
    private Long chapterId;

    /** 最近一次章节练习答对题数 */
    private Integer correctCount;

    /** 最近一次章节练习题数 */
    private Integer totalCount;

    /** 得分(0~100) */
    private Integer score;

    /** 是否完成过章节练习: 1-是 */
    private Integer finished;

    /** 最近练习时间 */
    private LocalDateTime lastPracticeAt;
}
