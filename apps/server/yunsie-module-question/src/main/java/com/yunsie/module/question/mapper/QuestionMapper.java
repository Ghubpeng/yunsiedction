package com.yunsie.module.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunsie.module.question.entity.Question;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
}
