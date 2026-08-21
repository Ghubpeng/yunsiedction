package com.yunsie.module.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.question.entity.QuestionMistake;
import com.yunsie.module.question.error.QuestionErrorCode;
import com.yunsie.module.question.mapper.QuestionMapper;
import com.yunsie.module.question.mapper.QuestionMistakeMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 错题本单元测试：幂等累加 / 本人数据保护。
 */
class MistakeServiceTest {

    private MistakeServiceImpl service(QuestionMistakeMapper mistakeMapper) {
        return new MistakeServiceImpl(mistakeMapper, mock(QuestionMapper.class));
    }

    @Test
    void recordWrong_firstTime_inserts() {
        QuestionMistakeMapper mapper = mock(QuestionMistakeMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        service(mapper).recordWrong(1L, 10L);

        ArgumentCaptor<QuestionMistake> captor = ArgumentCaptor.forClass(QuestionMistake.class);
        verify(mapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getMistakeCount());
        assertEquals(1, captor.getValue().getStatus());
    }

    @Test
    void recordWrong_secondTime_incrementsSingleRow() {
        QuestionMistakeMapper mapper = mock(QuestionMistakeMapper.class);
        QuestionMistake existing = new QuestionMistake();
        existing.setId(5L);
        existing.setUserId(1L);
        existing.setQuestionId(10L);
        existing.setMistakeCount(2);
        existing.setStatus(1);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        service(mapper).recordWrong(1L, 10L);

        verify(mapper, never()).insert(any(QuestionMistake.class));
        verify(mapper).updateById(existing);
        assertEquals(3, existing.getMistakeCount());
    }

    @Test
    void resolve_otherUsersMistake_30312() {
        QuestionMistakeMapper mapper = mock(QuestionMistakeMapper.class);
        QuestionMistake mistake = new QuestionMistake();
        mistake.setId(5L);
        mistake.setUserId(99L);
        when(mapper.selectById(5L)).thenReturn(mistake);

        BizException ex = assertThrows(BizException.class, () -> service(mapper).resolve(1L, 5L));
        assertEquals(QuestionErrorCode.MISTAKE_NOT_FOUND.code(), ex.getCode());
    }
}
