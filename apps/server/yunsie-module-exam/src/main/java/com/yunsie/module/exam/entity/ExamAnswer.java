package com.yunsie.module.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试作答明细表（exam_answer）。暂存 upsert 幂等；判分结果写入。
 */
@Getter
@Setter
@TableName("exam_answer")
public class ExamAnswer extends BaseEntity {

    /** 考试实例ID */
    private Long attemptId;

    /** 试卷题目快照ID */
    private Long paperQuestionId;

    /** 提交答案(暂存原文) */
    private String submittedAnswer;

    /** 是否正确: 1-是 0-否(交卷判分后写入) */
    private Integer correct;

    /** 本题得分(交卷判分后写入) */
    private BigDecimal score;

    /** 最近作答时间 */
    private LocalDateTime answeredAt;
}
