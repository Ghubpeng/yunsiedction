package com.yunsie.module.course.controller;

import com.yunsie.common.api.PageResult;
import com.yunsie.common.api.Result;
import com.yunsie.common.security.CurrentUser;
import com.yunsie.module.course.dto.ChapterPracticeReq;
import com.yunsie.module.course.dto.ProgressReq;
import com.yunsie.module.course.service.CourseService;
import com.yunsie.module.course.service.impl.ChapterPracticeServiceImpl;
import com.yunsie.module.course.service.impl.LearningPathServiceImpl;
import com.yunsie.module.course.service.impl.PlayServiceImpl;
import com.yunsie.module.course.service.impl.ProgressServiceImpl;
import com.yunsie.module.course.vo.ChapterPracticeResultVO;
import com.yunsie.module.course.vo.CourseTreeVO;
import com.yunsie.module.course.vo.LearningPathVO;
import com.yunsie.module.course.vo.MyCourseVO;
import com.yunsie.module.course.vo.PlayVO;
import com.yunsie.module.course.vo.PublicCourseVO;
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
 * 用户端课程接口（登录即可，无 RBAC 权限点；严格 @CurrentUser 本人数据）。
 */
@RestController
@RequestMapping("/api/v1/course")
@RequiredArgsConstructor
@Validated
public class CourseUserController {

    private final CourseService courseService;
    private final ProgressServiceImpl progressService;
    private final PlayServiceImpl playService;
    private final ChapterPracticeServiceImpl chapterPracticeService;
    private final LearningPathServiceImpl learningPathService;

    @GetMapping("/public/courses")
    public Result<PageResult<PublicCourseVO>> publicCourses(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum 最小为1") Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 最小为1")
            @Max(value = 100, message = "pageSize 最大100") Integer pageSize,
            @RequestParam(required = false) Long certificateId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(courseService.publicPage(pageNum, pageSize, certificateId, subjectId, keyword));
    }

    @GetMapping("/public/courses/{id}/tree")
    public Result<CourseTreeVO> publicTree(@CurrentUser Long userId, @PathVariable Long id) {
        return Result.ok(courseService.publicTree(id, userId));
    }

    /** 公开课程大纲（匿名可读，无进度/无播放凭证；登录后走 tree 接口带进度） */
    @GetMapping("/public/courses/{id}/outline")
    public Result<CourseTreeVO> publicOutline(@PathVariable Long id) {
        return Result.ok(courseService.publicTree(id, null));
    }

    @PostMapping("/progress")
    public Result<Void> progress(@CurrentUser Long userId, @Valid @RequestBody ProgressReq req) {
        progressService.upsert(userId, req);
        return Result.ok(null);
    }

    @GetMapping("/lessons/{id}/play")
    public Result<PlayVO> play(@CurrentUser Long userId, @PathVariable Long id) {
        return Result.ok(playService.play(userId, id));
    }

    @GetMapping("/my-courses")
    public Result<List<MyCourseVO>> myCourses(@CurrentUser Long userId) {
        return Result.ok(progressService.myCourses(userId));
    }

    /** 章节练习完成上报（Stage 2.4：判分由服务端完成，此处仅记录汇总；掌握=视频完成+练习完成） */
    @PostMapping("/chapters/{id}/practice-result")
    public Result<ChapterPracticeResultVO> chapterPracticeResult(@CurrentUser Long userId, @PathVariable Long id,
                                                                @Valid @RequestBody ChapterPracticeReq req) {
        return Result.ok(chapterPracticeService.record(userId, id, req.correctCount(), req.totalCount()));
    }

    /** 学习路径（Stage 2.4）：证书→科目→章节→课时 + 四态 + 总完成度/学习阶段/下一任务 */
    @GetMapping("/me/learning-path")
    public Result<LearningPathVO> learningPath(@CurrentUser Long userId,
                                               @RequestParam(required = false) Long certificateId) {
        return Result.ok(learningPathService.path(userId, certificateId));
    }
}
