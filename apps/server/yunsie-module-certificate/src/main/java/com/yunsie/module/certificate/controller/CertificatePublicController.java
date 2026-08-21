package com.yunsie.module.certificate.controller;

import com.yunsie.common.api.PageResult;
import com.yunsie.common.api.Result;
import com.yunsie.module.certificate.service.CertificateCategoryService;
import com.yunsie.module.certificate.service.CertificateService;
import com.yunsie.module.certificate.vo.CategoryVO;
import com.yunsie.module.certificate.vo.CertVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户公开读取接口（/api/v1/certificate/public/**）。
 * 登录即可访问（默认认证），无管理权限要求；只返回 enabled=1 且未逻辑删除的数据。
 * 普通用户没有任何修改入口（DataScope：公共基础数据只读）。
 */
@RestController
@RequestMapping("/api/v1/certificate/public")
@RequiredArgsConstructor
@Validated
public class CertificatePublicController {

    private final CertificateCategoryService categoryService;
    private final CertificateService certificateService;

    @GetMapping("/categories/tree")
    public Result<List<CategoryVO>> enabledTree() {
        return Result.ok(categoryService.enabledTree());
    }

    @GetMapping("/certificates")
    public Result<PageResult<CertVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum 最小为1") Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 最小为1")
            @Max(value = 100, message = "pageSize 最大100") Integer pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(certificateService.publicPage(pageNum, pageSize, categoryId, keyword));
    }

    @GetMapping("/certificates/{id}")
    public Result<CertVO> detail(@PathVariable Long id) {
        return Result.ok(certificateService.publicGet(id));
    }
}
