package com.yunsie.module.learning.profile.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户考试目标表（learn_user_goal）：用户级学习上下文（CONFLICTS #26）。
 * uk(user_id, certificate_id, subject_id, deleted) 去重；切换=状态流转（status 1=当前 0=历史），
 * 绝不物理删除历史目标（保留用户历史学习数据语义）。
 * deleted 逻辑删除由 MyBatis-Plus 全局配置处理（BaseEntity）。
 */
@Getter
@Setter
@TableName("learn_user_goal")
public class LearnUserGoal extends BaseEntity {

    /** 用户ID(user域) */
    private Long userId;

    /** 考试目标证书ID(certificate域) */
    private Long certificateId;

    /** 考试科目ID(subject域) */
    private Long subjectId;

    /** 目标版本ID(subject域; 选择时=证书当前版本) */
    private Long versionId;

    /** 状态: 1-当前目标 0-历史目标 */
    private Integer status;
}
