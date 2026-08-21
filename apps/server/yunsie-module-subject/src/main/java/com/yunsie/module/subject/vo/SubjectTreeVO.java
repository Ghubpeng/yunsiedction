package com.yunsie.module.subject.vo;

import java.util.List;

/**
 * 科目知识树 VO（考试科目 → 章节 → 知识点 → 子知识点，完整五级视图）。
 */
public record SubjectTreeVO(
        Long subjectId,
        String subjectName,
        List<NodeVO> children) {
}
