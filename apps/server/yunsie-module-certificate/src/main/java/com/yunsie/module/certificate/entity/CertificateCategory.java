package com.yunsie.module.certificate.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 证书分类表（certificate_category）：无限级树。
 * parent_id + path(物化路径) + level + sort + enabled；平台化，不写死任何证书。
 */
@Getter
@Setter
@TableName("certificate_category")
public class CertificateCategory extends BaseEntity {

    /** 父分类ID(0=根) */
    private Long parentId;

    /** 分类名称 */
    private String name;

    /** 分类编码 */
    private String code;

    /** 物化路径, 如 /1/2/ */
    private String path;

    /** 层级(根=1) */
    private Integer level;

    /** 排序(越小越靠前) */
    private Integer sort;

    /** 启用: 1-是 0-否 */
    private Integer enabled;

    /** 描述 */
    private String description;
}
