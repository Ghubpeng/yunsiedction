package com.yunsie.module.subject.controller;

import com.yunsie.common.api.Result;
import com.yunsie.module.subject.dto.VersionCreateReq;
import com.yunsie.module.subject.dto.VersionUpdateReq;
import com.yunsie.module.subject.service.SubjectVersionService;
import com.yunsie.module.subject.vo.VersionVO;
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
 * 知识体系版本管理接口（/api/v1/subject/versions，后台）。
 */
@RestController
@RequestMapping("/api/v1/subject/versions")
@RequiredArgsConstructor
@Validated
public class SubjectVersionController {

    private final SubjectVersionService versionService;

    @PostMapping
    @PreAuthorize("@perm.has('subject:create')")
    public Result<Long> create(@Valid @RequestBody VersionCreateReq req) {
        return Result.ok(versionService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('subject:update')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody VersionUpdateReq req) {
        versionService.update(id, req);
        return Result.ok(null);
    }

    @PutMapping("/{id}/current")
    @PreAuthorize("@perm.has('subject:manage')")
    public Result<Void> setCurrent(@PathVariable Long id) {
        versionService.setCurrent(id);
        return Result.ok(null);
    }

    @PutMapping("/{id}/archive")
    @PreAuthorize("@perm.has('subject:manage')")
    public Result<Void> archive(@PathVariable Long id) {
        versionService.archive(id);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('subject:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        versionService.delete(id);
        return Result.ok(null);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('subject:read')")
    public Result<VersionVO> detail(@PathVariable Long id) {
        return Result.ok(versionService.get(id));
    }

    @GetMapping
    @PreAuthorize("@perm.has('subject:read')")
    public Result<List<VersionVO>> list(@RequestParam Long certificateId) {
        return Result.ok(versionService.listByCertificate(certificateId));
    }
}
