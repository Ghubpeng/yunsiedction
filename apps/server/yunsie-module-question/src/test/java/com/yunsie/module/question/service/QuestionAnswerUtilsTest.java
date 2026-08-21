package com.yunsie.module.question.service;

import com.yunsie.common.exception.BizException;
import com.yunsie.module.question.enums.QuestionType;
import com.yunsie.module.question.error.QuestionErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 答案标准化单元测试（E 判分模型：避免选项顺序造成错误判分）。
 */
class QuestionAnswerUtilsTest {

    @Test
    void multiple_choice_orderInsensitive() {
        assertEquals("A|C", QuestionAnswerUtils.normalize("C|A", QuestionType.MULTIPLE_CHOICE));
        assertEquals("A|C", QuestionAnswerUtils.normalize("a, c", QuestionType.MULTIPLE_CHOICE));
    }

    @Test
    void multiple_choice_concatenatedLettersSplit() {
        assertEquals("A|C", QuestionAnswerUtils.normalize("AC", QuestionType.MULTIPLE_CHOICE));
    }

    @Test
    void single_choice_singleKeyOnly() {
        assertEquals("B", QuestionAnswerUtils.normalize("b", QuestionType.SINGLE_CHOICE));
        assertNull(QuestionAnswerUtils.normalize("AB", QuestionType.SINGLE_CHOICE), "单选不允许连写多键");
        assertNull(QuestionAnswerUtils.normalize("A|B", QuestionType.SINGLE_CHOICE), "单选不允许多键");
    }

    @Test
    void trueFalse_mappings() {
        assertEquals("T", QuestionAnswerUtils.normalize("对", QuestionType.TRUE_FALSE));
        assertEquals("T", QuestionAnswerUtils.normalize("√", QuestionType.TRUE_FALSE));
        assertEquals("F", QuestionAnswerUtils.normalize("false", QuestionType.TRUE_FALSE));
        assertEquals("F", QuestionAnswerUtils.normalize("错", QuestionType.TRUE_FALSE));
        assertNull(QuestionAnswerUtils.normalize("yes", QuestionType.TRUE_FALSE));
    }

    @Test
    void invalid_input_returnsNull() {
        assertNull(QuestionAnswerUtils.normalize("", QuestionType.SINGLE_CHOICE));
        assertNull(QuestionAnswerUtils.normalize("1", QuestionType.SINGLE_CHOICE));
    }

    @Test
    void withinKeys() {
        assertTrue(QuestionAnswerUtils.withinKeys("A|C", Set.of("A", "B", "C")));
        assertFalse(QuestionAnswerUtils.withinKeys("A|D", Set.of("A", "B", "C")));
        assertFalse(QuestionAnswerUtils.withinKeys(null, Set.of("A")));
    }

    @Test
    void gradingService_rejectsInvalidAnswer() {
        GradingService gradingService = new GradingService();
        BizException ex = assertThrows(BizException.class,
                () -> gradingService.normalizeSubmitted(QuestionType.SINGLE_CHOICE.code(), "1"));
        assertEquals(QuestionErrorCode.ANSWER_INVALID.code(), ex.getCode());
    }
}
