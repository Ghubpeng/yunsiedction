package com.yunsie.module.certificate.controller;

import com.yunsie.common.api.PageResult;
import com.yunsie.common.api.Result;
import com.yunsie.module.certificate.dto.CertCreateReq;
import com.yunsie.module.certificate.dto.CertUpdateReq;
import com.yunsie.module.certificate.service.CertificateService;
import com.yunsie.module.certificate.vo.CertVO;
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
 * 证书管理接口（/api/v1/certificate/certificates，后台）。
 */
@RestController
@RequestMapping("/api/v1/certificate/certificates")
@RequiredArgsConstructor
@Validated
public class CertificateController {

    private final CertificateService certificateService;

    @PostMapping
    @PreAuthorize("@perm.has('certificate:manage')")
    public Result<Long> create(@Valid @RequestBody CertCreateReq req) {
        return Result.ok(certificateService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('certificate:manage')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CertUpdateReq req) {
        certificateService.update(id, req);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('certificate:manage')")
    public Result<Void> delete(@PathVariable Long id) {
        certificateService.delete(id);
        return Result.ok(null);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('certificate:read')")
    public Result<CertVO> detail(@PathVariable Long id) {
        return Result.ok(certificateService.get(id));
    }

    @GetMapping
    @PreAuthorize("@perm.has('certificate:read')")
    public Result<PageResult<CertVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum 最小为1") Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 最小为1")
            @Max(value = 100, message = "pageSize 最大100") Integer pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled) {
        return Result.ok(certificateService.page(pageNum, pageSize, categoryId, keyword, enabled));
    }
}
