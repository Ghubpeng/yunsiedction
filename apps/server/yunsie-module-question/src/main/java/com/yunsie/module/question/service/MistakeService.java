package com.yunsie.module.question.service;

import com.yunsie.common.api.PageResult;
import com.yunsie.module.question.vo.MistakeVO;

/**
 * 错题本服务（用户本人数据，userId 由 Controller @CurrentUser 注入）。
 */
public interface MistakeService {

    /** 答错：幂等累加（uk 保证单行；并发冲突降级为更新） */
    void recordWrong(Long userId, Long questionId);

    /** 答对：如存在错题记录，仅更新最近练习时间（不自动解决） */
    void recordCorrect(Long userId, Long questionId);

    /** 手动解决 */
    void resolve(Long userId, Long mistakeId);

    PageResult<MistakeVO> page(Long userId, Integer status, int pageNum, int pageSize);
}
