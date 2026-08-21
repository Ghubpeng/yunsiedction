package com.yunsie.module.learning.profile.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 学习历史汇总表（learn_profile_summary）：一人一行派生投影。
 * 重算=物理删除本域行后重建（不改其他域数据）。
 */
@Getter
@Setter
@TableName("learn_profile_summary")
public class LearnProfileSummary extends BaseEntity {

    /** 用户ID(user域) */
    private Long userId;

    /** 总学习秒数(练习耗时+考试时长+课程进度秒数近似) */
    private Long totalStudySeconds;

    /** 已完成课程小节数 */
    private Integer courseFinishedLessons;

    /** 练习总数 */
    private Integer practiceCount;

    /** 练习正确总数 */
    private Integer practiceCorrectCount;

    /** 考试次数(已交卷) */
    private Integer examCount;

    /** 考试最佳成绩(无考试为 NULL) */
    private BigDecimal examBestScore;

    /** 连续学习天数 */
    private Integer streakDays;

    /** 最近学习日期 */
    private LocalDate lastStudyDate;
}
