package com.yunsie.module.course.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 课程章节表（course_chapter）：商业章节，与知识体系章节（subject 域）分离。
 */
@Getter
@Setter
@TableName("course_chapter")
public class CourseChapter extends BaseEntity {

    /** 所属课程ID */
    private Long courseId;

    /** 章节标题 */
    private String title;

    /** 排序(越小越靠前) */
    private Integer sort;

    /** 状态: 1-启用 0-禁用(已发布课程中禁用对学员隐藏) */
    private Integer status;
}
