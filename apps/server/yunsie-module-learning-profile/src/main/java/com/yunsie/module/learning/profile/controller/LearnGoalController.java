package com.yunsie.module.learning.profile.controller;

import com.yunsie.common.api.Result;
import com.yunsie.common.security.CurrentUser;
import com.yunsie.module.learning.profile.dto.GoalSelectReq;
import com.yunsie.module.learning.profile.service.GoalService;
import com.yunsie.module.learning.profile.vo.GoalVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户考试目标接口（/api/v1/learn/goals，登录即可，无 RBAC 权限点；严格本人数据）。
 */
@RestController
@RequestMapping("/api/v1/learn/goals")
@RequiredArgsConstructor
public class LearnGoalController {

    private final GoalService goalService;

    @GetMapping("/current")
    public Result<GoalVO> current(@CurrentUser Long userId) {
        return Result.ok(goalService.current(userId));
    }

    @GetMapping
    public Result<List<GoalVO>> list(@CurrentUser Long userId) {
        return Result.ok(goalService.list(userId));
    }

    @PostMapping("/select")
    public Result<GoalVO> select(@CurrentUser Long userId, @Valid @RequestBody GoalSelectReq req) {
        return Result.ok(goalService.select(userId, req));
    }
}
