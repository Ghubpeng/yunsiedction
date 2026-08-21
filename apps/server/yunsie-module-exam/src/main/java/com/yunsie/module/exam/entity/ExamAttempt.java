package com.yunsie.module.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试实例表（exam_attempt）：一次参加。成绩并入本表（MVP 客观题即时判分）。
 * 计时唯一依据=服务端：expired_at = started_at + duration；交卷条件更新幂等。
 */
@Getter
@Setter
@TableName("exam_attempt")
public class ExamAttempt extends BaseEntity {

    /** 所属考试ID */
    private Long examId;

    /** 绑定的试卷快照ID */
    private Long paperId;

    /** 用户ID(user域) */
    private Long userId;

    /** 状态: 1-未开始 2-进行中 3-已交卷 */
    private Integer status;

    /** 开始时间(服务端时间) */
    private LocalDateTime startedAt;

    /** 截止时间(服务端: started_at+duration; 计时唯一依据) */
    private LocalDateTime expiredAt;

    /** 交卷时间 */
    private LocalDateTime submittedAt;

    /** 成绩(交卷判分后写入) */
    private BigDecimal score;

    /** 答对题数 */
    private Integer correctCount;

    /** 试卷题数快照 */
    private Integer questionCount;
}
