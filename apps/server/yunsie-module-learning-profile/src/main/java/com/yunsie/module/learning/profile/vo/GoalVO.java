package com.yunsie.module.learning.profile.vo;

/**
 * 用户考试目标视图（含证书/科目名称，经 CertificateQueryApi / SubjectQueryApi 取）。
 */
public record GoalVO(
        Long id,
        Long certificateId,
        Long subjectId,
        Long versionId,
        Integer status,
        String certificateName,
        String subjectName) {
}
