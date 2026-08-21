package com.yunsie.module.subject.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建知识节点入参。
 * 层级规则：章节 parentId=0；知识点父节点必须为章节；子知识点父节点必须为知识点（服务层强制）。
 */
public record NodeCreateReq(
        @NotNull(message = "所属版本不能为空") Long versionId,
        @NotNull(message = "所属科目不能为空") Long subjectId,
        @Min(value = 0, message = "父节点ID非法") Long parentId,
        @NotNull(message = "节点类型不能为空")
        @Min(value = 1, message = "节点类型非法") @Max(value = 3, message = "节点类型非法")
        Integer nodeType,
        @NotBlank(message = "节点名称不能为空") @Size(max = 200, message = "节点名称最长200字符") String name,
        @Size(max = 500, message = "描述最长500字符") String description,
        @NotBlank(message = "节点编码不能为空") @Size(max = 100, message = "节点编码最长100字符") String code,
        Integer sort,
        @Min(value = 0, message = "启用状态非法") @Max(value = 1, message = "启用状态非法") Integer enabled,
        @Min(value = 1, message = "来源非法") @Max(value = 2, message = "来源非法") Integer source,
        @Size(max = 500, message = "备注最长500字符") String remark) {
}
