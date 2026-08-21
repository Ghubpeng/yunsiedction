package com.yunsie.module.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 试卷题目快照表（exam_paper_question）：历史判分唯一依据，不可变。
 * question_id 仅溯源（无物理外键）；题库后续修改/下架/删除不影响本表。
 */
@Getter
@Setter
@TableName("exam_paper_question")
public class ExamPaperQuestion extends BaseEntity {

    /** 所属试卷ID */
    private Long paperId;

    /** 源题目ID(仅溯源) */
    private Long questionId;

    /** 题号(从1开始) */
    private Integer sort;

    /** 本题分值快照(MVP每题1分) */
    private BigDecimal score;

    /** 题型快照: 1-单选 2-多选 3-判断 */
    private Integer questionType;

    /** 题干快照 */
    private String stem;

    /** 解析快照 */
    private String analysis;

    /** 标准答案快照(标准化答案串) */
    private String standardAnswer;

    /** 难度快照: 1-易 2-中 3-难 */
    private Integer difficulty;

    /** 来源快照: 1-真题 2-模拟题 3-自编 */
    private Integer source;

    /** 题目内容版本快照(溯源) */
    private Integer contentVersion;

    /** 知识点关联快照(逗号分隔, 溯源) */
    private String nodeIds;
}
