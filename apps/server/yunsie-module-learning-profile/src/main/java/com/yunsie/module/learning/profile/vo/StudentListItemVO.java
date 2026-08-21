package com.yunsie.module.learning.profile.vo;

/**
 * 教师视图：课程学员档案列表项（最小必要字段）。
 */
public record StudentListItemVO(
        Long userId,
        String nickname,
        SummaryVO summary) {
}
