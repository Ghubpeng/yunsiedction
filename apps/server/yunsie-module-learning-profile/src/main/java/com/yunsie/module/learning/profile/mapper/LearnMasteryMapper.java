package com.yunsie.module.learning.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunsie.module.learning.profile.entity.LearnMastery;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 知识点掌握度 Mapper。重算重建使用物理删除（派生投影表，本域自持）。
 */
@Mapper
public interface LearnMasteryMapper extends BaseMapper<LearnMastery> {

    /** 物理删除某用户全部掌握度行（重算重建专用） */
    @Delete("DELETE FROM learn_mastery WHERE user_id = #{userId}")
    int physicalDeleteByUser(@Param("userId") Long userId);
}
