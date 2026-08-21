package com.yunsie.module.course.controller;

import com.yunsie.common.api.PageResult;
import com.yunsie.common.api.Result;
import com.yunsie.common.security.CurrentUser;
import com.yunsie.module.course.dto.CourseCreateReq;
import com.yunsie.module.course.dto.CourseUpdateReq;
import com.yunsie.module.course.service.CourseService;
import com.yunsie.module.course.vo.CourseVO;
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
 * 课程管理接口（/api/v1/course/courses，后台）。
 * 权限统一 @perm.has；教师数据范围在服务层（CourseAccessGuard）。
 */
@RestController
@RequestMapping("/api/v1/course/courses")
@RequiredArgsConstructor
@Validated
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    @PreAuthorize("@perm.has('course:course:create')")
    public Result<Long> create(@CurrentUser Long currentUserId, @Valid @RequestBody CourseCreateReq req) {
        return Result.ok(courseService.create(req, currentUserId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('course:course:update')")
    public Result<Void> update(@CurrentUser Long currentUserId, @PathVariable Long id,
                               @Valid @RequestBody CourseUpdateReq req) {
        courseService.update(id, req, currentUserId);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('course:course:delete')")
    public Result<Void> delete(@CurrentUser Long currentUserId, @PathVariable Long id) {
        courseService.delete(id, currentUserId);
        return Result.ok(null);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@perm.has('course:course:publish')")
    public Result<Void> publish(@CurrentUser Long currentUserId, @PathVariable Long id) {
        courseService.publish(id, currentUserId);
        return Result.ok(null);
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("@perm.has('course:course:publish')")
    public Result<Void> unpublish(@CurrentUser Long currentUserId, @PathVariable Long id) {
        courseService.unpublish(id, currentUserId);
        return Result.ok(null);
    }

    @GetMapping
    @PreAuthorize("@perm.has('course:course:read')")
    public Result<PageResult<CourseVO>> page(
            @CurrentUser Long currentUserId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum 最小为1") Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 最小为1")
            @Max(value = 100, message = "pageSize 最大100") Integer pageSize,
            @RequestParam(required = false) Long certificateId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) @Min(value = 1, message = "状态非法")
            @Max(value = 3, message = "状态非法") Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.ok(courseService.page(currentUserId, pageNum, pageSize, certificateId, subjectId, status, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('course:course:read')")
    public Result<CourseVO> detail(@CurrentUser Long currentUserId, @PathVariable Long id) {
        return Result.ok(courseService.detail(id, currentUserId));
    }
}
