package com.yunsie.module.sys.vo;

/**
 * 数据范围 VO。
 */
public record DataScopeVO(
        Integer scopeType,
        String resourceType,
        Long resourceId) {
}
