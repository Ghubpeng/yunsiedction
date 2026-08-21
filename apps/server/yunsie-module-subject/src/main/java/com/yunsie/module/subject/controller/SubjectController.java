package com.yunsie.module.subject.controller;

import com.yunsie.common.api.Result;
import com.yunsie.module.subject.dto.SubjectCreateReq;
import com.yunsie.module.subject.dto.SubjectUpdateReq;
import com.yunsie.module.subject.service.ExamSubjectService;
import com.yunsie.module.subject.vo.SubjectVO;
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

import java.util.List;

/**
 * 考试科目管理接口（/api/v1/subject/subjects，后台）。
 */
@RestController
@RequestMapping("/api/v1/subject/subjects")
@RequiredArgsConstructor
@Validated
public class SubjectController {

    private final ExamSubjectService subjectService;

    @PostMapping
    @PreAuthorize("@perm.has('subject:create')")
    public Result<Long> create(@Valid @RequestBody SubjectCreateReq req) {
        return Result.ok(subjectService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('subject:update')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SubjectUpdateReq req) {
        subjectService.update(id, req);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('subject:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        subjectService.delete(id);
        return Result.ok(null);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('subject:read')")
    public Result<SubjectVO> detail(@PathVariable Long id) {
        return Result.ok(subjectService.get(id));
    }

    @GetMapping
    @PreAuthorize("@perm.has('subject:read')")
    public Result<List<SubjectVO>> list(@RequestParam Long certificateId) {
        return Result.ok(subjectService.listByCertificate(certificateId));
    }
}
