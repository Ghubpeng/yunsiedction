package com.yunsie.module.learning.profile.service;

import com.yunsie.module.learning.profile.dto.GoalSelectReq;
import com.yunsie.module.learning.profile.vo.GoalVO;

import java.util.List;

/**
 * 用户考试目标服务（学习上下文；切换=状态流转，绝不物理删除历史目标）。
 */
public interface GoalService {

    /** 当前目标（无目标返回 null） */
    GoalVO current(Long userId);

    /** 全部未删除目标（当前目标在前） */
    List<GoalVO> list(Long userId);

    /** 选择/切换考试目标（事务内状态流转；versionId=证书当前版本） */
    GoalVO select(Long userId, GoalSelectReq req);
}
