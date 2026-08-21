package com.yunsie.boot.ops;

import com.yunsie.common.api.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容运营看板接口（boot 装配点，Stage 2.3B）：
 * GET /api/v1/content/dashboard —— 内容资产统计 + 运营提醒（登录即可，聚合只读数据）。
 */
@RestController
@RequestMapping("/api/v1/content")
@RequiredArgsConstructor
public class ContentOpsController {

    private final ContentOpsService opsService;

    @GetMapping("/dashboard")
    public Result<ContentOpsVO> dashboard() {
        return Result.ok(opsService.snapshot());
    }
}
