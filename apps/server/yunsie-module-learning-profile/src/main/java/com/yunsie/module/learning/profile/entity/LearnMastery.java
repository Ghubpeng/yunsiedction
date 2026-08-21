package com.yunsie.module.learning.profile.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识点掌握度表（learn_mastery）：user x node x version_id 唯一（版本隔离，CONFLICTS #20）。
 * mastery_value 范围 0~100。
 */
@Getter
@Setter
@TableName("learn_mastery")
public class LearnMastery extends BaseEntity {

    /** 用户ID(user域) */
    private Long userId;

    /** 证书ID(certificate域, 冗余自节点) */
    private Long certificateId;

    /** 考试科目ID(subject域, 冗余自节点) */
    private Long subjectId;

    /** 知识节点ID(subject域) */
    private Long nodeId;

    /** 知识体系版本ID(subject_version; 版本隔离依据) */
    private Long versionId;

    /** 掌握度 0~100 */
    private Integer masteryValue;

    /** 该节点累计答对次数 */
    private Integer correctCount;

    /** 该节点累计答错次数 */
    private Integer wrongCount;

    /** 最近练习时间(时间衰减依据) */
    private LocalDateTime lastPracticeAt;

    /** 最近更新来源: PRACTICE/RECALC */
    private String lastTriggerSource;
}
