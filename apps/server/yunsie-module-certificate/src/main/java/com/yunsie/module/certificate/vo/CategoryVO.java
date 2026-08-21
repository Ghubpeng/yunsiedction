package com.yunsie.module.certificate.vo;

import java.util.List;

/**
 * 证书分类 VO（树形）。
 */
public record CategoryVO(
        Long id,
        Long parentId,
        String name,
        String code,
        String path,
        Integer level,
        Integer sort,
        Integer enabled,
        String description,
        List<CategoryVO> children) {
}
