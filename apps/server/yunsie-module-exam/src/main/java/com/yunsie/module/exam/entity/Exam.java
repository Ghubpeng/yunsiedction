package com.yunsie.module.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试定义表（exam_exam）。
 * 组卷规则配置化（assemble_rule JSON）；发布后锁定（不可编辑/组卷/删除）。
 */
@Getter
@Setter
@TableName("exam_exam")
public class Exam extends BaseEntity {

    /** 所属证书ID */
    private Long certificateId;

    /** 归属科目ID(subject域; 内容树归属; 空=仅证书级) */
    private Long subjectId;

    /** 归属版本ID(subject域; 内容树归属; 空=未指定) */
    private Long versionId;

    /** 考试名称 */
    private String name;

    /** 考试时长(分钟, 服务端计时依据) */
    private Integer durationMinutes;

    /** 总分(组卷后回填) */
    private BigDecimal totalScore;

    /** 及格线(可选; NULL=不判及格) */
    private BigDecimal passScore;

    /** 可参加开始时间(可选) */
    private LocalDateTime validFrom;

    /** 可参加截止时间(可选) */
    private LocalDateTime validUntil;

    /** 状态: 1-草稿 2-已发布 3-已下架 */
    private Integer status;

    /** 组卷规则JSON: questionCount/questionTypes/nodeIds/difficulty */
    private String assembleRule;
}
