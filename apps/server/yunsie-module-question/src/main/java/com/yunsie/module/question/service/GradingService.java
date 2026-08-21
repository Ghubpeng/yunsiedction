package com.yunsie.module.question.service;

import com.yunsie.common.exception.BizException;
import com.yunsie.module.question.enums.QuestionType;
import com.yunsie.module.question.error.QuestionErrorCode;
import org.springframework.stereotype.Service;

/**
 * 判分服务（E 判分模型，题型策略集中于此，Controller 零题型 if/else）。
 * 单选：单键完全匹配；多选：标准化集合完全匹配；判断：T/F 匹配。
 * 未实现题型判分默认拒绝（防静默误判）。
 */
@Service
public class GradingService {

    /** 判分：提交答案先标准化再与标准答案比较。 */
    public boolean grade(Integer questionType, String standardAnswer, String submittedRaw) {
        QuestionType type = QuestionType.of(questionType);
        if (type == null) {
            throw new BizException(QuestionErrorCode.QUESTION_TYPE_INVALID);
        }
        String normalized = QuestionAnswerUtils.normalize(submittedRaw, type);
        if (normalized == null) {
            throw new BizException(QuestionErrorCode.ANSWER_INVALID);
        }
        return switch (type) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE, TRUE_FALSE -> normalized.equals(standardAnswer);
        };
    }

    /** 提交答案标准化（供练习记录落库；非法输入抛 ANSWER_INVALID） */
    public String normalizeSubmitted(Integer questionType, String submittedRaw) {
        QuestionType type = QuestionType.of(questionType);
        if (type == null) {
            throw new BizException(QuestionErrorCode.QUESTION_TYPE_INVALID);
        }
        String normalized = QuestionAnswerUtils.normalize(submittedRaw, type);
        if (normalized == null) {
            throw new BizException(QuestionErrorCode.ANSWER_INVALID);
        }
        return normalized;
    }

    /** 校验并标准化题目标准答案（录入/导入共用）：选择题答案必须在选项键内；判断题 T/F */
    public String normalizeStandardAnswer(Integer questionType, String rawAnswer, java.util.Set<String> optionKeys) {
        QuestionType type = QuestionType.of(questionType);
        if (type == null) {
            throw new BizException(QuestionErrorCode.QUESTION_TYPE_INVALID);
        }
        String normalized = QuestionAnswerUtils.normalize(rawAnswer, type);
        if (normalized == null) {
            throw new BizException(QuestionErrorCode.ANSWER_INVALID);
        }
        if (type == QuestionType.TRUE_FALSE) {
            return normalized;
        }
        if (optionKeys == null || !QuestionAnswerUtils.withinKeys(normalized, optionKeys)) {
            throw new BizException(QuestionErrorCode.ANSWER_INVALID);
        }
        return normalized;
    }
}
