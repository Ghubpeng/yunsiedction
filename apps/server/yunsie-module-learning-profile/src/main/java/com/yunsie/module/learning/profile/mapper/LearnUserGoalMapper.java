package com.yunsie.module.learning.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunsie.module.learning.profile.entity.LearnUserGoal;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户考试目标 Mapper。逻辑删除由 MyBatis-Plus 全局配置处理，不手写 deleted 过滤。
 */
@Mapper
public interface LearnUserGoalMapper extends BaseMapper<LearnUserGoal> {
}
