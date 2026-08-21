package com.yunsie.module.learning.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunsie.module.learning.profile.entity.LearnConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习档案配置 Mapper（掌握度/预测参数，配置化）。
 */
@Mapper
public interface LearnConfigMapper extends BaseMapper<LearnConfig> {
}
