package com.yunsie.module.subject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建知识体系版本入参。
 */
public record VersionCreateReq(
        @NotNull(message = "所属证书不能为空") Long certificateId,
        @NotBlank(message = "版本号不能为空") @Size(max = 30, message = "版本号最长30字符") String versionNo,
        @NotBlank(message = "版本名称不能为空") @Size(max = 100, message = "版本名称最长100字符") String name,
        @Size(max = 500, message = "备注最长500字符") String remark) {
}
