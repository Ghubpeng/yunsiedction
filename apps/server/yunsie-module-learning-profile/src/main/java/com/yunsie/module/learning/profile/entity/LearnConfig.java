package com.yunsie.module.learning.profile.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 学习档案配置表（learn_config）：掌握度/预测参数配置化，learning-profile 域内自持（CONFLICTS #19）。
 * 禁止在算法/服务中硬编码魔法数字。
 */
@Getter
@Setter
@TableName("learn_config")
public class LearnConfig extends BaseEntity {

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置说明 */
    private String remark;
}
