package com.yunsie.module.learning.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunsie.module.learning.profile.entity.LearnStudyCalendar;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 学习日历 Mapper。重算重建使用物理删除（派生投影表，本域自持）。
 */
@Mapper
public interface LearnStudyCalendarMapper extends BaseMapper<LearnStudyCalendar> {

    /** 物理删除某用户全部日历行（重算重建专用） */
    @Delete("DELETE FROM learn_study_calendar WHERE user_id = #{userId}")
    int physicalDeleteByUser(@Param("userId") Long userId);
}
