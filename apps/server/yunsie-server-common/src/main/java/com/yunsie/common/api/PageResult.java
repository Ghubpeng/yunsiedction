package com.yunsie.common.api;

import java.io.Serializable;
import java.util.List;

/**
 * 分页出参结构（api-design：{@code {list, total}}）。
 *
 * @param <T> 列表元素类型
 */
public record PageResult<T>(List<T> list, long total) implements Serializable {

    public static <T> PageResult<T> of(List<T> list, long total) {
        return new PageResult<>(list, total);
    }
}
