package com.yunsie.module.learning.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.learning.profile.dto.GoalSelectReq;
import com.yunsie.module.learning.profile.entity.LearnUserGoal;
import com.yunsie.module.learning.profile.error.LearnErrorCode;
import com.yunsie.module.learning.profile.mapper.LearnUserGoalMapper;
import com.yunsie.module.learning.profile.service.GoalService;
import com.yunsie.module.learning.profile.vo.GoalVO;
import com.yunsie.module.subject.api.SubjectQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户考试目标实现。切换=状态流转（status 1→0/0→1），历史目标保留；
 * versionId 一律解析为证书当前版本（前端不自行判定版本）。
 */
@Service
@RequiredArgsConstructor
public class GoalServiceImpl implements GoalService {

    private final LearnUserGoalMapper goalMapper;
    private final CertificateQueryApi certificateQueryApi;
    private final SubjectQueryApi subjectQueryApi;

    @Override
    public GoalVO current(Long userId) {
        LearnUserGoal goal = goalMapper.selectOne(new LambdaQueryWrapper<LearnUserGoal>()
                .eq(LearnUserGoal::getUserId, userId)
                .eq(LearnUserGoal::getStatus, 1)
                .last("LIMIT 1"));
        return goal == null ? null : toVO(goal);
    }

    @Override
    public List<GoalVO> list(Long userId) {
        return goalMapper.selectList(new LambdaQueryWrapper<LearnUserGoal>()
                        .eq(LearnUserGoal::getUserId, userId)
                        .orderByDesc(LearnUserGoal::getStatus)
                        .orderByDesc(LearnUserGoal::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GoalVO select(Long userId, GoalSelectReq req) {
        requireCertificate(req.certificateId());
        // Stage 2.2：证书级目标（subjectId 空 → 0 哨兵 = 自动覆盖全部科目）；科目级能力保留
        long subjectKey = req.subjectId() == null ? 0L : req.subjectId();
        if (subjectKey > 0) {
            requireSubject(req.certificateId(), subjectKey);
        }
        Long versionId = subjectQueryApi.findCurrentVersionId(req.certificateId());
        if (versionId == null) {
            throw new BizException(LearnErrorCode.GOAL_NO_CURRENT_VERSION);
        }

        // 全部当前目标置为历史（切换=状态流转，不删历史学习数据）
        LearnUserGoal zero = new LearnUserGoal();
        zero.setStatus(0);
        goalMapper.update(zero, new LambdaUpdateWrapper<LearnUserGoal>()
                .eq(LearnUserGoal::getUserId, userId)
                .eq(LearnUserGoal::getStatus, 1));

        LearnUserGoal goal = goalMapper.selectOne(new LambdaQueryWrapper<LearnUserGoal>()
                .eq(LearnUserGoal::getUserId, userId)
                .eq(LearnUserGoal::getCertificateId, req.certificateId())
                .eq(LearnUserGoal::getSubjectId, subjectKey));
        if (goal == null) {
            goal = new LearnUserGoal();
            goal.setUserId(userId);
            goal.setCertificateId(req.certificateId());
            goal.setSubjectId(subjectKey);
            goal.setVersionId(versionId);
            goal.setStatus(1);
            goalMapper.insert(goal);
        } else {
            goal.setVersionId(versionId);
            goal.setStatus(1);
            goalMapper.updateById(goal);
        }
        return toVO(goal);
    }

    private void requireCertificate(Long certificateId) {
        CertificateQueryApi.CertificateView cert = certificateQueryApi.findCertificate(certificateId);
        if (cert == null || cert.enabled() == null || cert.enabled() != 1) {
            throw new BizException(LearnErrorCode.GOAL_CERT_INVALID);
        }
    }

    private void requireSubject(Long certificateId, Long subjectId) {
        SubjectQueryApi.SubjectView subject = subjectQueryApi.findSubject(subjectId);
        if (subject == null || subject.enabled() == null || subject.enabled() != 1
                || !certificateId.equals(subject.certificateId())) {
            throw new BizException(LearnErrorCode.GOAL_SUBJECT_INVALID);
        }
    }

    private GoalVO toVO(LearnUserGoal goal) {
        CertificateQueryApi.CertificateView cert = certificateQueryApi.findCertificate(goal.getCertificateId());
        // 0 哨兵 = 证书级目标（自动覆盖全部科目）→ 对外 subjectId/subjectName 为 null
        boolean certLevel = goal.getSubjectId() == null || goal.getSubjectId() == 0L;
        SubjectQueryApi.SubjectView subject = certLevel ? null : subjectQueryApi.findSubject(goal.getSubjectId());
        return new GoalVO(goal.getId(), goal.getCertificateId(), certLevel ? null : goal.getSubjectId(),
                goal.getVersionId(), goal.getStatus(),
                cert == null ? null : cert.name(), subject == null ? null : subject.name());
    }
}
