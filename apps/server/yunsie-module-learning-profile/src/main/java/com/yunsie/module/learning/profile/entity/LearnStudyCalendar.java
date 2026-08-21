package com.yunsie.module.learning.profile.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 学习日历表（learn_study_calendar）：user x study_date 聚合（连续学习天数依据）。
 * study_seconds = 练习耗时 + 考试时长；课程学习无逐日事件历史，仅标记学习日。
 */
@Getter
@Setter
@TableName("learn_study_calendar")
public class LearnStudyCalendar extends BaseEntity {

    /** 用户ID(user域) */
    private Long userId;

    /** 学习日期 */
    private LocalDate studyDate;

    /** 当日学习秒数 */
    private Long studySeconds;

    /** 当日练习次数 */
    private Integer practiceCount;

    /** 当日考试次数(已交卷) */
    private Integer examCount;
}
