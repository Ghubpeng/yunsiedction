package com.yunsie.module.question.controller;

import com.yunsie.common.api.Result;
import com.yunsie.module.question.service.ExcelImportService;
import com.yunsie.module.question.vo.ImportResultVO;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Excel 导入接口（/api/v1/question/import，后台）。
 * 同步导入、≤5000 行、行级错误、整体校验通过才入库（禁止部分入库）。
 */
@RestController
@RequestMapping("/api/v1/question")
@RequiredArgsConstructor
@Validated
public class QuestionImportController {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final ExcelImportService excelImportService;

    @PostMapping("/import")
    @PreAuthorize("@perm.has('question:question:import')")
    public Result<ImportResultVO> importFile(@RequestParam("file") MultipartFile file,
                                             @RequestParam @NotNull(message = "所属证书不能为空") Long certificateId)
            throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new com.yunsie.common.exception.BizException(
                    com.yunsie.module.question.error.QuestionErrorCode.IMPORT_FILE_INVALID);
        }
        return Result.ok(excelImportService.importFile(file.getBytes(), file.getOriginalFilename(), certificateId));
    }

    @GetMapping("/import-template")
    @PreAuthorize("@perm.has('question:question:import')")
    public ResponseEntity<byte[]> importTemplate() {
        byte[] bytes = excelImportService.template();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=question-import-template.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
