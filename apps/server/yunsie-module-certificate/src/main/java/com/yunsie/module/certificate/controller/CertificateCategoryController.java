package com.yunsie.module.certificate.controller;

import com.yunsie.common.api.Result;
import com.yunsie.module.certificate.dto.CategoryCreateReq;
import com.yunsie.module.certificate.dto.CategoryMoveReq;
import com.yunsie.module.certificate.dto.CategoryUpdateReq;
import com.yunsie.module.certificate.service.CertificateCategoryService;
import com.yunsie.module.certificate.vo.CategoryVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 证书分类管理接口（/api/v1/certificate/categories，后台）。
 * 权限统一走 @PreAuthorize("@perm.has('...')")（permission-rbac）。
 */
@RestController
@RequestMapping("/api/v1/certificate/categories")
@RequiredArgsConstructor
@Validated
public class CertificateCategoryController {

    private final CertificateCategoryService categoryService;

    @PostMapping
    @PreAuthorize("@perm.has('certificate:category:create')")
    public Result<Long> create(@Valid @RequestBody CategoryCreateReq req) {
        return Result.ok(categoryService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('certificate:category:update')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CategoryUpdateReq req) {
        categoryService.update(id, req);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('certificate:category:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok(null);
    }

    @GetMapping("/tree")
    @PreAuthorize("@perm.has('certificate:category:read')")
    public Result<List<CategoryVO>> tree() {
        return Result.ok(categoryService.tree());
    }

    @GetMapping("/{id}/children")
    @PreAuthorize("@perm.has('certificate:category:read')")
    public Result<List<CategoryVO>> children(@PathVariable Long id) {
        return Result.ok(categoryService.children(id));
    }

    @PostMapping("/{id}/move")
    @PreAuthorize("@perm.has('certificate:category:update')")
    public Result<Void> move(@PathVariable Long id, @Valid @RequestBody CategoryMoveReq req) {
        categoryService.move(id, req);
        return Result.ok(null);
    }
}
