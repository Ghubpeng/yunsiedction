package com.yunsie.boot.ops;

import com.yunsie.common.api.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容复制接口（boot 装配点，Stage 2.3B）：
 * POST /api/v1/content/certificates/{id}/copy —— 复制证书体系（科目/章节/知识点/课程结构/可选题库）。
 */
@RestController
@RequestMapping("/api/v1/content")
@RequiredArgsConstructor
public class ContentCopyController {

    private final ContentCopyService copyService;

    @PostMapping("/certificates/{id}/copy")
    @PreAuthorize("@perm.has('certificate:certificate:create')")
    public Result<CopySummaryVO> copy(@PathVariable Long id, @Valid @RequestBody CopyCertificateReq req) {
        return Result.ok(copyService.copy(id, req));
    }
}
