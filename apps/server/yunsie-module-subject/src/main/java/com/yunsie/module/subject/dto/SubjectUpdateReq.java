package com.yunsie.module.subject.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新考试科目入参（code 创建后不可变）。
 */
public record SubjectUpdateReq(
        @NotBlank(message = "科目名称不能为空") @Size(max = 100, message = "科目名称最长100字符") String name,
        Integer sort,
        @Min(value = 0, message = "启用状态非法") @Max(value = 1, message = "启用状态非法") Integer enabled,
        @Size(max = 500, message = "描述最长500字符") String description) {
}
