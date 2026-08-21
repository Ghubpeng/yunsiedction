package com.yunsie.module.subject.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新知识节点入参（code/nodeType/versionId/subjectId 创建后不可变）。
 */
public record NodeUpdateReq(
        @NotBlank(message = "节点名称不能为空") @Size(max = 200, message = "节点名称最长200字符") String name,
        @Size(max = 500, message = "描述最长500字符") String description,
        Integer sort,
        @Min(value = 0, message = "启用状态非法") @Max(value = 1, message = "启用状态非法") Integer enabled,
        @Min(value = 1, message = "来源非法") @Max(value = 2, message = "来源非法") Integer source,
        @Size(max = 500, message = "备注最长500字符") String remark) {
}
