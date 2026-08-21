package com.yunsie.module.course.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 课程表（course_course）：商业内容实体（课程 != 考试科目）。
 * 无价格/VIP 字段（商业规则归 pay 域）；type=2 直播为预留扩展位，不实现。
 */
@Getter
@Setter
@TableName("course_course")
public class Course extends BaseEntity {

    /** 所属证书ID */
    private Long certificateId;

    /** 归属科目ID(subject域; 内容树: 证书→版本→科目→课程; 空=未指定) */
    private Long subjectId;

    /** 归属版本ID(subject域; 创建时默认证书当前版本; 空=未指定) */
    private Long versionId;

    /** 归属章节ID(subject域; 内容树: 证书→版本→科目→章节→课程; 空=未指定) */
    private Long chapterId;

    /** 教师用户ID(user域, 教师数据范围依据) */
    private Long teacherId;

    /** 课程标题 */
    private String title;

    /** 课程简介 */
    private String description;

    /** 类型: 1-录播 2-直播(预留, 不实现) */
    private Integer type;

    /** 状态: 1-草稿 2-已发布 3-已下架 */
    private Integer status;

    /** 封面对象键(预留, MVP 不实现封面上传) */
    private String coverKey;
}
