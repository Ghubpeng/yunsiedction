package com.yunsie.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类（database-design 必备字段）。
 * id 自增主键 / create_time / update_time / deleted 逻辑删除 / version 乐观锁。
 * 逻辑删除由 boot 全局配置 logic-delete-field=deleted 生效；时间字段由 DB 默认值填充。
 */
@Getter
@Setter
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除: 0-否 1-是 */
    private Integer deleted;

    /** 乐观锁版本号 */
    private Integer version;
}
