package com.yunsie.module.course.vo;

/**
 * 小节 VO（管理端/用户端树共用；用户端含进度字段）。
 */
public record LessonVO(
        Long id,
        String title,
        Integer durationSeconds,
        Integer sort,
        Integer status,
        Integer positionSeconds,
        Integer finished) {
}
