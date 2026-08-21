package com.yunsie.module.learning.profile.vo;

import java.util.List;

/**
 * 教师/管理员视图：单个学员完整档案（summary + 掌握度 + 薄弱点）。
 */
public record StudentProfileVO(
        Long userId,
        String nickname,
        SummaryVO summary,
        List<MasteryVO> mastery,
        List<WeaknessVO> weakness) {
}
