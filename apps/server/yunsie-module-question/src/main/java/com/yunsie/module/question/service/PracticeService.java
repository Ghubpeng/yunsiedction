package com.yunsie.module.question.service;

import com.yunsie.module.question.dto.PracticeSubmitReq;
import com.yunsie.module.question.vo.PracticeQuestionVO;
import com.yunsie.module.question.vo.PracticeSubmitResultVO;

import java.util.List;

/**
 * 练习服务（用户端，登录即可，无 RBAC 权限点）。
 * 四种模式：1-顺序 2-分类 3-知识点 4-错题；仅抽取已发布题目。
 */
public interface PracticeService {

    /**
     * 抽取练习题（不含答案/解析）。
     * 顺序/分类：certificateId 必填 + cursor 游标；知识点：nodeId 必填（知识点含子知识点）；错题：本人未解决。
     */
    List<PracticeQuestionVO> next(Long userId, int mode, Long certificateId, Long nodeId,
                                  Long cursor, int size, int pageNum, int pageSize);

    /** 提交作答：标准化判分 + 练习记录 + 错题自动收集/更新 */
    PracticeSubmitResultVO submit(Long userId, PracticeSubmitReq req);
}
