package com.yunsie.module.question.controller;

import com.yunsie.common.api.PageResult;
import com.yunsie.common.api.Result;
import com.yunsie.common.security.CurrentUser;
import com.yunsie.module.question.dto.NodeIdsReq;
import com.yunsie.module.question.dto.QuestionCreateReq;
import com.yunsie.module.question.dto.QuestionRejectReq;
import com.yunsie.module.question.dto.QuestionUpdateReq;
import com.yunsie.module.question.service.QuestionService;
import com.yunsie.module.question.vo.QuestionDetailVO;
import com.yunsie.module.question.vo.QuestionVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题目管理接口（/api/v1/question/questions，后台）。
 * 录入员（create/update/delete/read）与审核员（audit）权限分离；
 * 发布唯一通道 = 审核通过（approve），不可绕过（question-bank 铁律）。
 */
@RestController
@RequestMapping("/api/v1/question/questions")
@RequiredArgsConstructor
@Validated
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping
    @PreAuthorize("@perm.has('question:question:create')")
    public Result<Long> create(@Valid @RequestBody QuestionCreateReq req) {
        return Result.ok(questionService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('question:question:update')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody QuestionUpdateReq req) {
        questionService.update(id, req);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('question:question:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return Result.ok(null);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('question:question:read')")
    public Result<QuestionDetailVO> detail(@PathVariable Long id) {
        return Result.ok(questionService.detail(id));
    }

    @GetMapping
    @PreAuthorize("@perm.has('question:question:read')")
    public Result<PageResult<QuestionVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum 最小为1") Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 最小为1")
            @Max(value = 100, message = "pageSize 最大100") Integer pageSize,
            @RequestParam(required = false) Long certificateId,
            @RequestParam(required = false) @Min(value = 1, message = "状态非法")
            @Max(value = 6, message = "状态非法") Integer status,
            @RequestParam(required = false) @Min(value = 1, message = "题型非法")
            @Max(value = 3, message = "题型非法") Integer questionType,
            @RequestParam(required = false) @Min(value = 1, message = "难度非法")
            @Max(value = 3, message = "难度非法") Integer difficulty,
            @RequestParam(required = false) String keyword) {
        return Result.ok(questionService.page(pageNum, pageSize, certificateId, status, questionType, difficulty, keyword));
    }

    @PutMapping("/{id}/knowledge-nodes")
    @PreAuthorize("@perm.has('question:question:update')")
    public Result<Void> updateKnowledgeNodes(@PathVariable Long id, @Valid @RequestBody NodeIdsReq req) {
        questionService.updateKnowledgeNodes(id, req.nodeIds());
        return Result.ok(null);
    }

    @PostMapping("/{id}/submit-review")
    @PreAuthorize("@perm.has('question:question:create')")
    public Result<Void> submitReview(@PathVariable Long id) {
        questionService.submitReview(id);
        return Result.ok(null);
    }

    /** 撤回审核（Stage 2.3A：审核中 → 草稿） */
    @PostMapping("/{id}/withdraw-review")
    @PreAuthorize("@perm.has('question:question:create')")
    public Result<Void> withdrawReview(@PathVariable Long id) {
        questionService.withdrawReview(id);
        return Result.ok(null);
    }

    /** 从回收站恢复（Stage 2.3B：回收站 → 草稿，重新走审核） */
    @PostMapping("/{id}/restore")
    @PreAuthorize("@perm.has('question:question:update')")
    public Result<Void> restore(@PathVariable Long id) {
        questionService.restore(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("@perm.has('question:question:audit')")
    public Result<Void> approve(@PathVariable Long id, @CurrentUser Long auditorId) {
        questionService.approve(id, auditorId);
        return Result.ok(null);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("@perm.has('question:question:audit')")
    public Result<Void> reject(@PathVariable Long id, @CurrentUser Long auditorId,
                               @Valid @RequestBody QuestionRejectReq req) {
        questionService.reject(id, req.reason(), auditorId);
        return Result.ok(null);
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("@perm.has('question:question:update')")
    public Result<Void> unpublish(@PathVariable Long id) {
        questionService.unpublish(id);
        return Result.ok(null);
    }
}
