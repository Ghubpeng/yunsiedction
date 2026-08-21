package com.yunsie.module.exam.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 试卷题目快照 VO（管理端查看试卷用，含答案与解析）。
 */
public record PaperQuestionVO(
        Long paperQuestionId,
        Long questionId,
        Integer sort,
        BigDecimal score,
        Integer questionType,
        String stem,
        String analysis,
        String standardAnswer,
        Integer difficulty,
        Integer source,
        Integer contentVersion,
        String nodeIds,
        List<OptionVO> options) {

    public record OptionVO(String optionKey, String content) {
    }
}
