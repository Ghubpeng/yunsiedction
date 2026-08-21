package com.yunsie.module.learning.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunsie.module.learning.profile.entity.LearnProfileSummary;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 学习历史汇总 Mapper。重算重建使用物理删除（learn_* 为派生投影表，本域自持；
 * 逻辑删除会占用 uk(user_id, deleted) 导致重复重建冲突）。
 */
@Mapper
public interface LearnProfileSummaryMapper extends BaseMapper<LearnProfileSummary> {

    /** 物理删除某用户全部汇总行（重算重建专用） */
    @Delete("DELETE FROM learn_profile_summary WHERE user_id = #{userId}")
    int physicalDeleteByUser(@Param("userId") Long userId);
}
