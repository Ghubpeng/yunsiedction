package com.yunsie.module.subject.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建考试科目入参。
 */
public record SubjectCreateReq(
        @NotNull(message = "所属证书不能为空") Long certificateId,
        @NotBlank(message = "科目名称不能为空") @Size(max = 100, message = "科目名称最长100字符") String name,
        @NotBlank(message = "科目编码不能为空") @Size(max = 50, message = "科目编码最长50字符") String code,
        Integer sort,
        @Min(value = 0, message = "启用状态非法") @Max(value = 1, message = "启用状态非法") Integer enabled,
        @Min(value = 1, message = "来源非法") @Max(value = 2, message = "来源非法") Integer source,
        @Size(max = 500, message = "描述最长500字符") String description) {
}
