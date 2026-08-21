package com.yunsie.module.course.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 学习进度表（course_learn_progress）：自动续播依据。
 * (user_id, lesson_id) 唯一 upsert；position 按 duration 截断；
 * finished = position >= duration * 阈值（阈值配置化，见 application.yml）。
 */
@Getter
@Setter
@TableName("course_learn_progress")
public class CourseLearnProgress extends BaseEntity {

    /** 用户ID(user域) */
    private Long userId;

    /** 小节ID */
    private Long lessonId;

    /** 已观看秒数(按 duration 截断) */
    private Integer positionSeconds;

    /** 时长快照(上报时的小节时长) */
    private Integer durationSeconds;

    /** 是否完成: 1-是 0-否 */
    private Integer finished;

    /** 最近学习时间(续播排序依据) */
    private LocalDateTime lastLearnTime;
}
