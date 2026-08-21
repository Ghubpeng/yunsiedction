package com.yunsie.module.certificate.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 证书表（certificate_cert）：具体考试证书。
 * 当前知识体系版本不冗余在本表，见 subject 域 subject_version.status=2（当前）。
 */
@Getter
@Setter
@TableName("certificate_cert")
public class Certificate extends BaseEntity {

    /** 所属分类ID */
    private Long categoryId;

    /** 证书名称 */
    private String name;

    /** 证书编码 */
    private String code;

    /** 简称 */
    private String shortName;

    /** 描述 */
    private String description;

    /** 启用: 1-是 0-否 */
    private Integer enabled;

    /** 排序(越小越靠前) */
    private Integer sort;
}
