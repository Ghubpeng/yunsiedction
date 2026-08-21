package com.yunsie.module.learning.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunsie.module.learning.profile.entity.LearnEventDedup;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习事件幂等去重 Mapper（append-only；uk(dedup_key) 兜底防重复）。
 */
@Mapper
public interface LearnEventDedupMapper extends BaseMapper<LearnEventDedup> {
}
