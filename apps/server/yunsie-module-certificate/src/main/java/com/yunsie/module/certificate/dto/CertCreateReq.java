package com.yunsie.module.certificate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建证书入参。
 */
public record CertCreateReq(
        @NotNull(message = "所属分类不能为空") Long categoryId,
        @NotBlank(message = "证书名称不能为空") @Size(max = 100, message = "证书名称最长100字符") String name,
        @NotBlank(message = "证书编码不能为空") @Size(max = 50, message = "证书编码最长50字符") String code,
        @Size(max = 50, message = "简称最长50字符") String shortName,
        @Size(max = 500, message = "描述最长500字符") String description,
        Integer sort,
        @Min(value = 0, message = "启用状态非法") @Max(value = 1, message = "启用状态非法") Integer enabled) {
}
