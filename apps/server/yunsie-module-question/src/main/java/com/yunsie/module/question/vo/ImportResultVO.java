package com.yunsie.module.question.vo;

import java.util.List;

/**
 * Excel 导入结果 VO。
 * 行级错误整体不落库（question-bank 铁律：禁止部分入库）。
 */
public record ImportResultVO(
        boolean success,
        int total,
        int successCount,
        List<ImportErrorItem> errors) {

    public record ImportErrorItem(
            int row,
            String field,
            String message) {
    }

    public static ImportResultVO ok(int total) {
        return new ImportResultVO(true, total, total, List.of());
    }

    public static ImportResultVO failed(int total, List<ImportErrorItem> errors) {
        return new ImportResultVO(false, total, 0, errors);
    }
}
