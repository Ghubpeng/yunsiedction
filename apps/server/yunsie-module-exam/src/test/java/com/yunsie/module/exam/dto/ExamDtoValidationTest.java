package com.yunsie.module.exam.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 考试 DTO / 组卷规则校验单元测试（Bean Validation）。
 */
class ExamDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private ExamCreateReq req(int questionCount, List<Integer> types, Integer difficulty, int duration) {
        return new ExamCreateReq(1L, null, null, "模拟考试", duration, null, null, null,
                new ExamCreateReq.AssembleRule(questionCount, types, null, difficulty));
    }

    @Test
    void validRule_passes() {
        assertTrue(validator.validate(req(10, List.of(1, 2, 3), null, 60)).isEmpty());
    }

    @Test
    void invalidQuestionCount_fails() {
        assertFalse(validator.validate(req(0, List.of(1), null, 60)).isEmpty());
        assertFalse(validator.validate(req(201, List.of(1), null, 60)).isEmpty());
    }

    @Test
    void emptyTypes_fails() {
        assertFalse(validator.validate(req(10, List.of(), null, 60)).isEmpty());
    }

    @Test
    void invalidTypeCode_fails() {
        assertFalse(validator.validate(req(10, List.of(9), null, 60)).isEmpty());
    }

    @Test
    void invalidDifficulty_fails() {
        assertFalse(validator.validate(req(10, List.of(1), 5, 60)).isEmpty());
    }

    @Test
    void invalidDuration_fails() {
        assertFalse(validator.validate(req(10, List.of(1), null, 0)).isEmpty());
        assertFalse(validator.validate(req(10, List.of(1), null, 301)).isEmpty());
    }
}
