package com.yunsie.module.course.controller;

import com.yunsie.common.api.Result;
import com.yunsie.common.security.CurrentUser;
import com.yunsie.module.course.dto.ChapterCreateReq;
import com.yunsie.module.course.dto.ChapterUpdateReq;
import com.yunsie.module.course.dto.LessonCreateReq;
import com.yunsie.module.course.dto.LessonNodeIdsReq;
import com.yunsie.module.course.dto.LessonUpdateReq;
import com.yunsie.module.course.dto.StatusReq;
import com.yunsie.module.course.service.impl.CourseChapterServiceImpl;
import com.yunsie.module.course.service.impl.CourseLessonServiceImpl;
import com.yunsie.module.course.vo.ChapterVO;
import jakarta.validation.Valid;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 章节/小节/视频管理接口（/api/v1/course/chapters、/api/v1/course/lessons，后台）。
 * 说明：采用扁平资源路径（与既有 sys/role 风格一致），等价于设计的嵌套路径语义。
 */
@RestController
@RequestMapping("/api/v1/course")
@RequiredArgsConstructor
@Validated
public class CourseContentController {

    private final CourseChapterServiceImpl chapterService;
    private final CourseLessonServiceImpl lessonService;

    // ---------- 章节 ----------

    @PostMapping("/chapters")
    @PreAuthorize("@perm.has('course:course:update')")
    public Result<Long> createChapter(@CurrentUser Long currentUserId, @Valid @RequestBody ChapterCreateReq req) {
        return Result.ok(chapterService.create(req, currentUserId));
    }

    @PutMapping("/chapters/{id}")
    @PreAuthorize("@perm.has('course:course:update')")
    public Result<Void> updateChapter(@CurrentUser Long currentUserId, @PathVariable Long id,
                                      @Valid @RequestBody ChapterUpdateReq req) {
        chapterService.update(id, req, currentUserId);
        return Result.ok(null);
    }

    @PutMapping("/chapters/{id}/status")
    @PreAuthorize("@perm.has('course:course:update')")
    public Result<Void> updateChapterStatus(@CurrentUser Long currentUserId, @PathVariable Long id,
                                            @Valid @RequestBody StatusReq req) {
        chapterService.updateStatus(id, req.status(), currentUserId);
        return Result.ok(null);
    }

    @DeleteMapping("/chapters/{id}")
    @PreAuthorize("@perm.has('course:course:delete')")
    public Result<Void> deleteChapter(@CurrentUser Long currentUserId, @PathVariable Long id) {
        chapterService.delete(id, currentUserId);
        return Result.ok(null);
    }

    @GetMapping("/courses/{courseId}/chapters")
    @PreAuthorize("@perm.has('course:course:read')")
    public Result<List<ChapterVO>> adminTree(@CurrentUser Long currentUserId, @PathVariable Long courseId) {
        return Result.ok(chapterService.adminTree(courseId, currentUserId));
    }

    // ---------- 小节 ----------

    @PostMapping("/lessons")
    @PreAuthorize("@perm.has('course:course:update')")
    public Result<Long> createLesson(@CurrentUser Long currentUserId, @Valid @RequestBody LessonCreateReq req) {
        return Result.ok(lessonService.create(req, currentUserId));
    }

    @PutMapping("/lessons/{id}")
    @PreAuthorize("@perm.has('course:course:update')")
    public Result<Void> updateLesson(@CurrentUser Long currentUserId, @PathVariable Long id,
                                     @Valid @RequestBody LessonUpdateReq req) {
        lessonService.update(id, req, currentUserId);
        return Result.ok(null);
    }

    @PutMapping("/lessons/{id}/status")
    @PreAuthorize("@perm.has('course:course:update')")
    public Result<Void> updateLessonStatus(@CurrentUser Long currentUserId, @PathVariable Long id,
                                           @Valid @RequestBody StatusReq req) {
        lessonService.updateStatus(id, req.status(), currentUserId);
        return Result.ok(null);
    }

    @DeleteMapping("/lessons/{id}")
    @PreAuthorize("@perm.has('course:course:delete')")
    public Result<Void> deleteLesson(@CurrentUser Long currentUserId, @PathVariable Long id) {
        lessonService.delete(id, currentUserId);
        return Result.ok(null);
    }

    @PutMapping("/lessons/{id}/knowledge-nodes")
    @PreAuthorize("@perm.has('course:course:update')")
    public Result<Void> updateKnowledgeNodes(@CurrentUser Long currentUserId, @PathVariable Long id,
                                             @Valid @RequestBody LessonNodeIdsReq req) {
        lessonService.updateKnowledgeNodes(id, req, currentUserId);
        return Result.ok(null);
    }

    @PostMapping("/lessons/{id}/video")
    @PreAuthorize("@perm.has('course:video:upload')")
    public Result<Void> uploadVideo(@CurrentUser Long currentUserId, @PathVariable Long id,
                                    @RequestParam("file") MultipartFile file) throws IOException {
        lessonService.uploadVideo(id, file, currentUserId);
        return Result.ok(null);
    }
}
