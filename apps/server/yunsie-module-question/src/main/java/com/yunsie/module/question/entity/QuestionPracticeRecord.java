package com.yunsie.module.question.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 练习作答记录表（question_practice_record）：简单行为事件源（学习档案后续聚合）。
 * standard_answer 为作答时刻的标准答案快照（不可变）。
 */
@Getter
@Setter
@TableName("question_practice_record")
public class QuestionPracticeRecord extends BaseEntity {

    /** 用户ID(user域) */
    private Long userId;

    /** 题目ID */
    private Long questionId;

    /** 练习模式: 1-顺序 2-分类 3-知识点 4-错题 */
    private Integer practiceMode;

    /** 提交答案(标准化后) */
    private String submittedAnswer;

    /** 标准答案快照(不可变) */
    private String standardAnswer;

    /** 是否正确: 1-是 0-否 */
    private Integer correct;

    /** 作答时间 */
    private LocalDateTime answerTime;

    /** 响应耗时(毫秒) */
    private Integer responseTimeMs;

    /** 知识点模式的目标节点ID(如有) */
    private Long knowledgeNodeId;
}
