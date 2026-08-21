package com.yunsie.module.question.service;

import com.yunsie.module.question.vo.ImportResultVO;

/**
 * Excel 导入服务（G 模型）：同步、≤5000 行、行级错误、整体校验通过才入库（禁止部分入库）。
 */
public interface ExcelImportService {

    /** 导入（.xlsx 字节 + 文件名 + 证书ID）。整体失败时返回行级错误，不落库。 */
    ImportResultVO importFile(byte[] fileBytes, String originalFilename, Long certificateId);

    /** 生成导入模板（.xlsx 字节，含表头 + 示例行） */
    byte[] template();
}
