package com.yunsie.module.question.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 题目选项表（question_option）。判断题无选项。
 */
@Getter
@Setter
@TableName("question_option")
public class QuestionOption extends BaseEntity {

    /** 题目ID */
    private Long questionId;

    /** 选项键 A-Z */
    private String optionKey;

    /** 选项内容 */
    private String content;

    /** 排序(越小越靠前) */
    private Integer sort;
}
