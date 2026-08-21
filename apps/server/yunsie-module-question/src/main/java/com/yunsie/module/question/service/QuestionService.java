package com.yunsie.module.question.service;

import com.yunsie.common.api.PageResult;
import com.yunsie.module.question.dto.QuestionCreateReq;
import com.yunsie.module.question.dto.QuestionUpdateReq;
import com.yunsie.module.question.vo.QuestionDetailVO;
import com.yunsie.module.question.vo.QuestionVO;

import java.util.List;

/**
 * 题目管理服务（题库生命周期：录入→审核→发布→使用→下架）。
 */
public interface QuestionService {

    Long create(QuestionCreateReq req);

    /** 编辑：草稿/驳回/已下架直接改；已发布 → content_version+1 并回草稿重审；待审核不可编辑 */
    void update(Long id, QuestionUpdateReq req);

    /** 覆盖式关联知识点（同 update 的编辑规则） */
    void updateKnowledgeNodes(Long id, List<Long> nodeIds);

    /** 删除：草稿/驳回/已下架逻辑删除；审核中拒绝（先撤回）；已发布 → 回收站（历史数据保护） */
    void delete(Long id);

    /** 恢复：仅回收站 → 草稿（重新走审核发布通道） */
    void restore(Long id);

    void submitReview(Long id);

    /** 撤回审核：仅待审核 → 草稿（Stage 2.3A 生命周期） */
    void withdrawReview(Long id);

    /** 发布唯一通道 = 审核通过（question-bank 铁律：发布不绕过审核） */
    void approve(Long id, Long auditorId);

    void reject(Long id, String reason, Long auditorId);

    void unpublish(Long id);

    QuestionDetailVO detail(Long id);

    PageResult<QuestionVO> page(int pageNum, int pageSize, Long certificateId, Integer status,
                                Integer questionType, Integer difficulty, String keyword);
}
