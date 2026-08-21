package com.yunsie.module.question.controller;

import com.yunsie.common.api.Result;
import com.yunsie.common.security.CurrentUser;
import com.yunsie.module.course.api.CourseQueryApi;
import com.yunsie.module.question.api.QuestionQueryApi;
import com.yunsie.module.question.dto.PracticeSubmitReq;
import com.yunsie.module.question.service.PracticeService;
import com.yunsie.module.question.vo.PracticeQuestionVO;
import com.yunsie.module.question.vo.PracticeSubmitResultVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 练习接口（/api/v1/question/practice，用户端，登录即可，无 RBAC 权限点）。
 * 仅抽取已发布题目；提交不携带答案（答案由服务端判分）。
 */
@RestController
@RequestMapping("/api/v1/question/practice")
@RequiredArgsConstructor
@Validated
public class QuestionPracticeController {

    private final PracticeService practiceService;
    private final CourseQueryApi courseQueryApi;
    private final QuestionQueryApi questionQueryApi;

    @GetMapping("/next")
    public Result<List<PracticeQuestionVO>> next(
            @CurrentUser Long userId,
            @RequestParam @Min(value = 1, message = "练习模式非法") @Max(value = 4, message = "练习模式非法")
            Integer mode,
            @RequestParam(required = false) Long certificateId,
            @RequestParam(required = false) Long nodeId,
            @RequestParam(required = false) @Min(value = 0, message = "游标非法") Long cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 最小为1")
            @Max(value = 50, message = "size 最大50") Integer size,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum 最小为1") Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 最小为1")
            @Max(value = 100, message = "pageSize 最大100") Integer pageSize) {
        return Result.ok(practiceService.next(userId, mode, certificateId, nodeId, cursor, size, pageNum, pageSize));
    }

    @PostMapping("/submit")
    public Result<PracticeSubmitResultVO> submit(@CurrentUser Long userId, @Valid @RequestBody PracticeSubmitReq req) {
        return Result.ok(practiceService.submit(userId, req));
    }

    /**
     * 章节练习：按课程章节关联的知识点节点取已发布题目（练习安全视图，不含答案与解析）。
     * 登录即可；判分仍走 POST /practice/submit（不改）。
     */
    @GetMapping("/chapter/{chapterId}")
    public Result<List<QuestionQueryApi.PracticeQuestionView>> chapterPractice(
            @CurrentUser Long userId, @PathVariable Long chapterId) {
        List<Long> nodeIds = courseQueryApi.chapterNodeIds(chapterId);
        if (nodeIds.isEmpty()) {
            return Result.ok(List.of());
        }
        return Result.ok(questionQueryApi.listPublishedByNodes(nodeIds, 20));
    }
}
