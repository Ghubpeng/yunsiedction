package com.yunsie.module.exam.controller;

import com.yunsie.common.api.PageResult;
import com.yunsie.common.api.Result;
import com.yunsie.module.exam.dto.ExamCreateReq;
import com.yunsie.module.exam.dto.ExamUpdateReq;
import com.yunsie.module.exam.service.ExamService;
import com.yunsie.module.exam.vo.ExamVO;
import com.yunsie.module.exam.vo.PaperQuestionVO;
import com.yunsie.module.exam.vo.PaperSummaryVO;
import com.yunsie.module.exam.vo.SubmitResultVO;
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

import java.util.List;

/**
 * 考试管理接口（/api/v1/exam/exams，后台）。
 * 权限统一 @PreAuthorize("@perm.has('...')")（permission-rbac）。
 */
@RestController
@RequestMapping("/api/v1/exam/exams")
@RequiredArgsConstructor
@Validated
public class ExamController {

    private final ExamService examService;

    @PostMapping
    @PreAuthorize("@perm.has('exam:exam:create')")
    public Result<Long> create(@Valid @RequestBody ExamCreateReq req) {
        return Result.ok(examService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('exam:exam:update')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ExamUpdateReq req) {
        examService.update(id, req);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('exam:exam:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        examService.delete(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/assemble")
    @PreAuthorize("@perm.has('exam:exam:assemble')")
    public Result<Long> assemble(@PathVariable Long id) {
        return Result.ok(examService.assemble(id));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@perm.has('exam:exam:publish')")
    public Result<Void> publish(@PathVariable Long id) {
        examService.publish(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("@perm.has('exam:exam:publish')")
    public Result<Void> unpublish(@PathVariable Long id) {
        examService.unpublish(id);
        return Result.ok(null);
    }

    @GetMapping
    @PreAuthorize("@perm.has('exam:exam:read')")
    public Result<PageResult<ExamVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum 最小为1") Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 最小为1")
            @Max(value = 100, message = "pageSize 最大100") Integer pageSize,
            @RequestParam(required = false) Long certificateId,
            @RequestParam(required = false) @Min(value = 1, message = "状态非法")
            @Max(value = 3, message = "状态非法") Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.ok(examService.page(pageNum, pageSize, certificateId, status, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('exam:exam:read')")
    public Result<ExamVO> detail(@PathVariable Long id) {
        return Result.ok(examService.detail(id));
    }

    @GetMapping("/{id}/paper")
    @PreAuthorize("@perm.has('exam:exam:read')")
    public Result<PaperSummaryVO> paperSummary(@PathVariable Long id) {
        return Result.ok(examService.paperSummary(id));
    }

    @GetMapping("/{id}/paper/questions")
    @PreAuthorize("@perm.has('exam:exam:read')")
    public Result<List<PaperQuestionVO>> paperQuestions(@PathVariable Long id) {
        return Result.ok(examService.paperQuestions(id));
    }

    @GetMapping("/{id}/results")
    @PreAuthorize("@perm.has('exam:result:read')")
    public Result<PageResult<SubmitResultVO>> results(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum 最小为1") Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 最小为1")
            @Max(value = 100, message = "pageSize 最大100") Integer pageSize) {
        return Result.ok(examService.resultsPage(id, pageNum, pageSize));
    }
}
