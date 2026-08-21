package com.yunsie.module.exam.controller;

import com.yunsie.common.api.PageResult;
import com.yunsie.common.api.Result;
import com.yunsie.common.security.CurrentUser;
import com.yunsie.module.exam.dto.SaveAnswersReq;
import com.yunsie.module.exam.service.AttemptService;
import com.yunsie.module.exam.vo.AttemptResultVO;
import com.yunsie.module.exam.vo.AttemptStartVO;
import com.yunsie.module.exam.vo.AvailableExamVO;
import com.yunsie.module.exam.vo.SubmitResultVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
 * 用户端考试接口（/api/v1/exam/**，登录即可，无 RBAC 权限点）。
 * 严格本人数据：attemptId 归属校验在服务层（ATTEMPT_FORBIDDEN），无 userId 横向入参。
 */
@RestController
@RequestMapping("/api/v1/exam")
@RequiredArgsConstructor
@Validated
public class ExamUserController {

    private final AttemptService attemptService;

    @GetMapping("/available")
    public Result<List<AvailableExamVO>> available(@CurrentUser Long userId,
                                                   @RequestParam(required = false) Long certificateId) {
        return Result.ok(attemptService.available(userId, certificateId));
    }

    @PostMapping("/exams/{examId}/attempts")
    public Result<AttemptStartVO> start(@CurrentUser Long userId, @PathVariable Long examId) {
        return Result.ok(attemptService.start(userId, examId));
    }

    @GetMapping("/attempts/{attemptId}")
    public Result<AttemptStartVO> getAttempt(@CurrentUser Long userId, @PathVariable Long attemptId) {
        return Result.ok(attemptService.getAttempt(userId, attemptId));
    }

    @PutMapping("/attempts/{attemptId}/answers")
    public Result<Void> saveAnswers(@CurrentUser Long userId, @PathVariable Long attemptId,
                                    @Valid @RequestBody SaveAnswersReq req) {
        attemptService.saveAnswers(userId, attemptId, req);
        return Result.ok(null);
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public Result<SubmitResultVO> submit(@CurrentUser Long userId, @PathVariable Long attemptId) {
        return Result.ok(attemptService.submit(userId, attemptId));
    }

    @GetMapping("/attempts/{attemptId}/result")
    public Result<AttemptResultVO> result(@CurrentUser Long userId, @PathVariable Long attemptId) {
        return Result.ok(attemptService.result(userId, attemptId));
    }

    @GetMapping("/my-results")
    public Result<PageResult<SubmitResultVO>> myResults(
            @CurrentUser Long userId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum 最小为1") Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 最小为1")
            @Max(value = 100, message = "pageSize 最大100") Integer pageSize) {
        return Result.ok(attemptService.myResults(userId, pageNum, pageSize));
    }
}
