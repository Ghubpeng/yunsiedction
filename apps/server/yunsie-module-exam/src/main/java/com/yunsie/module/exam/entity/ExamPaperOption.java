package com.yunsie.module.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 试卷选项快照表（exam_paper_option）。判断题无行；不可变。
 */
@Getter
@Setter
@TableName("exam_paper_option")
public class ExamPaperOption extends BaseEntity {

    /** 试卷题目快照ID */
    private Long paperQuestionId;

    /** 选项键快照 A-Z */
    private String optionKey;

    /** 选项内容快照 */
    private String content;

    /** 排序快照 */
    private Integer sort;
}
