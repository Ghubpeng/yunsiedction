package com.yunsie.module.learning.profile.controller;

import com.yunsie.common.api.Result;
import com.yunsie.module.learning.profile.service.ProfileQueryService;
import com.yunsie.module.learning.profile.service.ProfileSyncService;
import com.yunsie.module.learning.profile.vo.SummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 档案重算兜底接口（learn:profile:recalc）：
 * 事件异步消费的崩溃窗口丢失自愈（CONFLICTS #18）；全量重建、幂等、不改其他域数据。
 */
@RestController
@RequestMapping("/api/v1/learn/admin")
@RequiredArgsConstructor
public class LearnAdminController {

    private final ProfileSyncService syncService;
    private final ProfileQueryService queryService;

    @PreAuthorize("@perm.has('learn:profile:recalc')")
    @PostMapping("/recalc/{userId}")
    public Result<SummaryVO> recalc(@PathVariable Long userId) {
        syncService.sync(userId);
        return Result.ok(queryService.summaryOf(userId));
    }
}
