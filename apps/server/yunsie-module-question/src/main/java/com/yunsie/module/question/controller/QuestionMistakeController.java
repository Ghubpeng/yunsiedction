package com.yunsie.module.question.controller;

import com.yunsie.common.api.PageResult;
import com.yunsie.common.api.Result;
import com.yunsie.common.security.CurrentUser;
import com.yunsie.module.question.service.MistakeService;
import com.yunsie.module.question.vo.MistakeVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 错题本接口（/api/v1/question/mistakes，用户端）。
 * 数据范围：仅本人（@CurrentUser，无 userId 入参 → 零横向越权面）。
 */
@RestController
@RequestMapping("/api/v1/question/mistakes")
@RequiredArgsConstructor
@Validated
public class QuestionMistakeController {

    private final MistakeService mistakeService;

    @GetMapping
    public Result<PageResult<MistakeVO>> page(
            @CurrentUser Long userId,
            @RequestParam(required = false) @Min(value = 1, message = "状态非法")
            @Max(value = 2, message = "状态非法") Integer status,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum 最小为1") Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 最小为1")
            @Max(value = 100, message = "pageSize 最大100") Integer pageSize) {
        return Result.ok(mistakeService.page(userId, status, pageNum, pageSize));
    }

    @PostMapping("/{id}/resolve")
    public Result<Void> resolve(@CurrentUser Long userId, @PathVariable Long id) {
        mistakeService.resolve(userId, id);
        return Result.ok(null);
    }
}
