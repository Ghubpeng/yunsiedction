package com.yunsie.boot.ops;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 复制证书体系入参（Stage 2.3B）。
 */
public record CopyCertificateReq(
        @NotBlank(message = "目标证书名称不能为空") @Size(max = 100, message = "证书名称最长100字符") String name,
        @NotBlank(message = "目标证书编码不能为空") @Size(max = 50, message = "证书编码最长50字符") String code,
        /** 目标分类；空=沿用源证书分类 */
        Long categoryId,
        /** 是否复制已发布题库（目标为草稿）；默认 false */
        Boolean copyQuestions,
        /** 目标证书描述（可选） */
        @Size(max = 500, message = "描述最长500字符") String description) {
}
