package com.yunsie.module.sys.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色数据权限范围表（sys_data_scope）。DataScope（permission-rbac）：
 * 教师/学员等角色的数据范围统一由本表配置，禁止业务代码散落判断。
 */
@Getter
@Setter
@TableName("sys_data_scope")
public class SysDataScope extends BaseEntity {

    /** 角色ID */
    private Long roleId;

    /** 范围类型: 1-全部数据 2-指定范围 */
    private Integer scopeType;

    /** 资源类型: *-全部; course-课程; certificate-证书等(按业务扩展) */
    private String resourceType;

    /** 资源ID; NULL=该资源类型全部 */
    private Long resourceId;
}
