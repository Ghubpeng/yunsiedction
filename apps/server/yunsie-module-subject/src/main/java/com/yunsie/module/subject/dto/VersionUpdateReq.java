package com.yunsie.module.subject.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新知识体系版本入参（状态经 /current /archive 专用接口变更）。
 */
public record VersionUpdateReq(
        @NotBlank(message = "版本名称不能为空") @Size(max = 100, message = "版本名称最长100字符") String name,
        @Size(max = 500, message = "备注最长500字符") String remark,
        @Min(value = 0, message = "启用状态非法") @Max(value = 1, message = "启用状态非法") Integer enabled) {
}
