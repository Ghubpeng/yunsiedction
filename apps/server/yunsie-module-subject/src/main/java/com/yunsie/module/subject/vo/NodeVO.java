package com.yunsie.module.subject.vo;

import java.util.List;

/**
 * 知识节点 VO（树形：章节 → 知识点 → 子知识点）。
 */
public record NodeVO(
        Long id,
        Long parentId,
        Integer nodeType,
        String name,
        String description,
        String code,
        String path,
        Integer level,
        Integer sort,
        Integer enabled,
        Integer source,
        List<NodeVO> children) {
}
