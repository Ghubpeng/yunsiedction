package com.yunsie.module.subject.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 知识体系版本 VO。
 */
public record VersionVO(
        Long id,
        Long certificateId,
        String versionNo,
        String name,
        Integer status,
        Integer enabled,
        String remark,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime createTime) {
}
