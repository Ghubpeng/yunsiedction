package com.yunsie.module.subject.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识体系版本表（subject_version）。
 * 每证书至多一个「当前」版本（status=2），应用层保证；版本化使掌握度/引用可追溯（后续阶段绑定）。
 */
@Getter
@Setter
@TableName("subject_version")
public class SubjectVersion extends BaseEntity {

    /** 所属证书ID */
    private Long certificateId;

    /** 版本号, 如 2026 */
    private String versionNo;

    /** 版本名称 */
    private String name;

    /** 状态: 1-草稿 2-当前 3-归档 */
    private Integer status;

    /** 启用: 1-是 0-否 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}
