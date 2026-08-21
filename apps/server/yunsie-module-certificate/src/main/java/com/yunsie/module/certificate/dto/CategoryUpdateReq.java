package com.yunsie.module.certificate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新证书分类入参（code 创建后不可变）。
 */
public record CategoryUpdateReq(
        @NotBlank(message = "分类名称不能为空") @Size(max = 100, message = "分类名称最长100字符") String name,
        Integer sort,
        @Min(value = 0, message = "启用状态非法") @Max(value = 1, message = "启用状态非法") Integer enabled,
        @Size(max = 500, message = "描述最长500字符") String description) {
}
