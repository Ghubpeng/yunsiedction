package com.yunsie.module.question.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 错题本表（question_mistake）。用户+题目唯一：重复答错累加不重复建行（幂等）。
 */
@Getter
@Setter
@TableName("question_mistake")
public class QuestionMistake extends BaseEntity {

    /** 用户ID(user域) */
    private Long userId;

    /** 题目ID */
    private Long questionId;

    /** 累计答错次数 */
    private Integer mistakeCount;

    /** 最近答错时间 */
    private LocalDateTime lastMistakeTime;

    /** 最近练习时间 */
    private LocalDateTime lastPracticeTime;

    /** 状态: 1-未解决 2-已解决 */
    private Integer status;
}
