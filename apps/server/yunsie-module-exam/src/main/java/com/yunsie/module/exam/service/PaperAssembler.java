package com.yunsie.module.exam.service;

import com.yunsie.module.exam.dto.ExamCreateReq;
import com.yunsie.module.exam.entity.Exam;

import java.math.BigDecimal;

/**
 * 组卷装配器：规则组卷 + 试卷快照（exam-engine：快照不可变、种子可复现、候选不足明确失败）。
 */
public interface PaperAssembler {

    /**
     * 组卷：旧有效卷作废，按规则抽题（QuestionQueryApi）→ Random(seed) 洗牌 → 落试卷快照。
     *
     * @param exam 考试定义（含组卷规则）
     * @return 新有效试卷 ID
     */
    Long assemble(Exam exam, ExamCreateReq.AssembleRule rule);
}
