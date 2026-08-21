package com.yunsie.module.exam.service;

import com.yunsie.common.api.PageResult;
import com.yunsie.module.exam.dto.ExamCreateReq;
import com.yunsie.module.exam.dto.ExamUpdateReq;
import com.yunsie.module.exam.vo.ExamVO;
import com.yunsie.module.exam.vo.PaperQuestionVO;
import com.yunsie.module.exam.vo.PaperSummaryVO;
import com.yunsie.module.exam.vo.SubmitResultVO;

/**
 * 考试管理服务（定义/组卷/发布/成绩查询）。
 */
public interface ExamService {

    Long create(ExamCreateReq req);

    /** 仅草稿/已下架可编辑 */
    void update(Long id, ExamUpdateReq req);

    /** 仅草稿/已下架可删；已有考试记录禁止删除（保护历史成绩） */
    void delete(Long id);

    /** 规则组卷：旧有效卷作废 + 新卷生效；仅草稿/已下架可组卷 */
    Long assemble(Long id);

    /** 发布：草稿/已下架 → 已发布；必须存在有效试卷 */
    void publish(Long id);

    void unpublish(Long id);

    ExamVO detail(Long id);

    PageResult<ExamVO> page(int pageNum, int pageSize, Long certificateId, Integer status, String keyword);

    PaperSummaryVO paperSummary(Long examId);

    /** 管理端查看试卷快照（含答案/解析） */
    java.util.List<PaperQuestionVO> paperQuestions(Long examId);

    /** 管理端成绩列表（已交卷记录） */
    PageResult<SubmitResultVO> resultsPage(Long examId, int pageNum, int pageSize);
}
