package com.yunsie.module.certificate.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 证书 VO。
 */
public record CertVO(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String code,
        String shortName,
        String description,
        Integer enabled,
        Integer sort,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime createTime) {
}
