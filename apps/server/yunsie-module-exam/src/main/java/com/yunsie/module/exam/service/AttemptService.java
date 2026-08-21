package com.yunsie.module.exam.service;

import com.yunsie.common.api.PageResult;
import com.yunsie.module.exam.dto.SaveAnswersReq;
import com.yunsie.module.exam.vo.AttemptResultVO;
import com.yunsie.module.exam.vo.AttemptStartVO;
import com.yunsie.module.exam.vo.AvailableExamVO;
import com.yunsie.module.exam.vo.SubmitResultVO;

import java.util.List;

/**
 * 考试实例服务（用户端：开始/上下文/暂存/交卷/成绩）。
 * 计时唯一依据=服务端；交卷条件更新幂等；attempt 严格本人数据。
 */
public interface AttemptService {

    /** 开始考试（幂等：存在进行中 attempt 直接返回；已交卷可重考=新 attempt） */
    AttemptStartVO start(Long userId, Long examId);

    /** 考试上下文（本人；超时懒结算自动交卷；含已暂存答案，断点续答） */
    AttemptStartVO getAttempt(Long userId, Long attemptId);

    /** 批量暂存答案（upsert 幂等；仅进行中且未超时） */
    void saveAnswers(Long userId, Long attemptId, SaveAnswersReq req);

    /** 交卷（幂等：条件更新；并发/重复交卷返回首次成绩，不重复计分） */
    SubmitResultVO submit(Long userId, Long attemptId);

    /** 成绩详情（本人；仅已交卷） */
    AttemptResultVO result(Long userId, Long attemptId);

    /** 本人成绩列表 */
    PageResult<SubmitResultVO> myResults(Long userId, int pageNum, int pageSize);

    /** 可参加考试列表（已发布+窗口内+有效试卷） */
    List<AvailableExamVO> available(Long userId);

    /** 可参加考试列表（追加式：可选按证书过滤，null=不过滤） */
    List<AvailableExamVO> available(Long userId, Long certificateId);

    /** 超时兜底自动交卷（懒结算与 @Scheduled 扫描共用；无本人校验，仅内部调用） */
    void autoSubmit(Long attemptId);
}
