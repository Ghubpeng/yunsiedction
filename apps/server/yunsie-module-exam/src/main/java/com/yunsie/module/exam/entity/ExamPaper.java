package com.yunsie.module.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 试卷快照容器表（exam_paper）。生成后不可变；重新组卷=旧卷作废+新卷生效。
 * 业务代码禁止对快照表执行逻辑删除（历史成绩依赖）。
 */
@Getter
@Setter
@TableName("exam_paper")
public class ExamPaper extends BaseEntity {

    /** 所属考试ID */
    private Long examId;

    /** 试卷标题(考试名快照) */
    private String title;

    /** 时长快照(分钟) */
    private Integer durationMinutes;

    /** 总分快照 */
    private BigDecimal totalScore;

    /** 题目数量快照 */
    private Integer questionCount;

    /** 状态: 1-有效 2-作废 */
    private Integer status;

    /** 组卷随机种子(可复现) */
    private Long assembleSeed;
}
