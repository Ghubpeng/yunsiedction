package com.yunsie.module.learning.profile.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 选择考试目标入参（Stage 2.2：用户只选证书，门槛最低化）。
 * subjectId 可选：为空 = 证书级目标（自动覆盖该证书全部科目，存储用 0 哨兵）；
 * 传入 = 科目级目标（Stage 2.1 能力保留）。versionId 由服务端解析为证书当前版本。
 */
public record GoalSelectReq(
        @NotNull(message = "考试目标证书不能为空") Long certificateId,
        Long subjectId) {
}
