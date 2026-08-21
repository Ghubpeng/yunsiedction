package com.yunsie.module.subject.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 考试科目表（subject_subject）。
 * 铁律：考试科目 != 课程（course 是商业内容实体，后续 course 域实现，经关联关系连接）。
 */
@Getter
@Setter
@TableName("subject_subject")
public class ExamSubject extends BaseEntity {

    /** 所属证书ID */
    private Long certificateId;

    /** 考试科目名称 */
    private String name;

    /** 科目编码 */
    private String code;

    /** 排序(越小越靠前) */
    private Integer sort;

    /** 启用: 1-是 0-否 */
    private Integer enabled;

    /** 来源: 1-官方大纲 2-平台自建 */
    private Integer source;

    /** 描述 */
    private String description;
}
