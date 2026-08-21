package com.yunsie.module.question.service.impl;

import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.certificate.error.CertErrorCode;
import com.yunsie.module.question.entity.Question;
import com.yunsie.module.question.entity.QuestionKnowledgeNode;
import com.yunsie.module.question.entity.QuestionOption;
import com.yunsie.module.question.enums.QuestionStatus;
import com.yunsie.module.question.enums.QuestionType;
import com.yunsie.module.question.error.QuestionErrorCode;
import com.yunsie.module.question.mapper.QuestionKnowledgeNodeMapper;
import com.yunsie.module.question.mapper.QuestionMapper;
import com.yunsie.module.question.mapper.QuestionOptionMapper;
import com.yunsie.module.question.service.ExcelImportService;
import com.yunsie.module.question.service.GradingService;
import com.yunsie.module.question.service.QuestionAnswerUtils;
import com.yunsie.module.question.vo.ImportResultVO;
import com.yunsie.module.subject.api.SubjectQueryApi;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Excel 导入实现。
 * 模板列：题型 | 题干 | 选项A..F | 答案 | 解析 | 难度 | 来源 | 知识点编码
 * 策略（Q3 + question-bank 铁律）：整体解析→行级校验→任一行错误整体不落库；≤5000 行，超过直接拒绝。
 */
@Service
@RequiredArgsConstructor
public class ExcelImportServiceImpl implements ExcelImportService {

    public static final int MAX_ROWS = 5000;
    private static final String[] HEADERS = {
            "题型", "题干", "选项A", "选项B", "选项C", "选项D", "选项E", "选项F",
            "答案", "解析", "难度", "来源", "知识点编码"};

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper optionMapper;
    private final QuestionKnowledgeNodeMapper knowledgeNodeMapper;
    private final GradingService gradingService;
    private final SubjectQueryApi subjectQueryApi;
    private final CertificateQueryApi certificateQueryApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importFile(byte[] fileBytes, String originalFilename, Long certificateId) {
        if (certificateQueryApi.findCertificate(certificateId) == null) {
            throw new BizException(CertErrorCode.CERT_NOT_FOUND);
        }
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".xlsx")) {
            throw new BizException(QuestionErrorCode.IMPORT_FILE_INVALID);
        }
        List<ImportRow> rows;
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            if (!validHeader(sheet.getRow(0))) {
                throw new BizException(QuestionErrorCode.IMPORT_HEADER_INVALID);
            }
            int dataRows = Math.max(sheet.getLastRowNum(), 0); // 表头占 1 行
            if (dataRows > MAX_ROWS) {
                throw new BizException(QuestionErrorCode.IMPORT_TOO_MANY_ROWS);
            }
            rows = parseRows(sheet);
        } catch (BizException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BizException(QuestionErrorCode.IMPORT_FILE_INVALID);
        }

        // 行级校验（不含 DB 依赖）
        List<ImportResultVO.ImportErrorItem> errors = new ArrayList<>();
        for (ImportRow row : rows) {
            validateRow(row, errors);
        }
        // 知识点编码解析（证书当前版本）
        Map<String, SubjectQueryApi.KnowledgeNodeView> nodeByCode = resolveNodeCodes(certificateId, rows);
        for (ImportRow row : rows) {
            validateNodeCodes(row, nodeByCode, errors);
        }
        // 重复题检测：文件内 + 库内（同证书、未删除、题干一致）
        detectDuplicates(certificateId, rows, errors);

        if (!errors.isEmpty()) {
            return ImportResultVO.failed(rows.size(), errors);
        }

        // 全部通过才入库（禁止部分入库）
        for (ImportRow row : rows) {
            insertRow(certificateId, row, nodeByCode);
        }
        return ImportResultVO.ok(rows.size());
    }

    @Override
    public byte[] template() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("题库导入模板");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("单选");
            example.createCell(1).setCellValue("示例题干：无菌技术操作的首要原则是？");
            example.createCell(2).setCellValue("选项A示例");
            example.createCell(3).setCellValue("选项B示例");
            example.createCell(4).setCellValue("选项C示例");
            example.createCell(5).setCellValue("选项D示例");
            example.createCell(8).setCellValue("A");
            example.createCell(9).setCellValue("示例解析");
            example.createCell(10).setCellValue("易");
            example.createCell(11).setCellValue("自编");
            example.createCell(12).setCellValue("KP_DEMO(请替换为实际知识点编码)");
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(QuestionErrorCode.IMPORT_FILE_INVALID);
        }
    }

    // ---------- 解析 ----------

    private boolean validHeader(Row headerRow) {
        if (headerRow == null) {
            return false;
        }
        DataFormatter formatter = new DataFormatter();
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.getCell(i);
            String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
            if (!HEADERS[i].equals(value)) {
                return false;
            }
        }
        return true;
    }

    private List<ImportRow> parseRows(Sheet sheet) {
        DataFormatter formatter = new DataFormatter();
        List<ImportRow> rows = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isBlankRow(row, formatter)) {
                continue;
            }
            ImportRow importRow = new ImportRow();
            importRow.rowNum = i + 1;
            importRow.typeLabel = cell(row, 0, formatter);
            importRow.stem = cell(row, 1, formatter);
            importRow.optionA = cell(row, 2, formatter);
            importRow.optionB = cell(row, 3, formatter);
            importRow.optionC = cell(row, 4, formatter);
            importRow.optionD = cell(row, 5, formatter);
            importRow.optionE = cell(row, 6, formatter);
            importRow.optionF = cell(row, 7, formatter);
            importRow.answer = cell(row, 8, formatter);
            importRow.analysis = cell(row, 9, formatter);
            importRow.difficultyLabel = cell(row, 10, formatter);
            importRow.sourceLabel = cell(row, 11, formatter);
            importRow.nodeCodes = cell(row, 12, formatter);
            rows.add(importRow);
        }
        return rows;
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && !formatter.formatCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String cell(Row row, int index, DataFormatter formatter) {
        Cell cell = row.getCell(index);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }

    // ---------- 行级校验 ----------

    private void validateRow(ImportRow row, List<ImportResultVO.ImportErrorItem> errors) {
        QuestionType type = typeOfLabel(row.typeLabel);
        if (type == null) {
            errors.add(err(row, "题型", "题型必须是：单选/多选/判断"));
            return;
        }
        row.type = type;
        if (row.stem.isBlank()) {
            errors.add(err(row, "题干", "题干不能为空"));
        }
        if (row.analysis.isBlank()) {
            errors.add(err(row, "解析", "解析不能为空（无解析不得发布）"));
        }
        // 选项与答案
        Map<String, String> options = new LinkedHashMap<>();
        String[][] optionCells = {{"选项A", row.optionA}, {"选项B", row.optionB}, {"选项C", row.optionC},
                {"选项D", row.optionD}, {"选项E", row.optionE}, {"选项F", row.optionF}};
        for (String[] entry : optionCells) {
            if (!entry[1].isBlank()) {
                options.put(entry[0].substring(2), entry[1]);
            }
        }
        row.options = options;
        if (type == QuestionType.TRUE_FALSE) {
            if (!options.isEmpty()) {
                errors.add(err(row, "选项", "判断题不允许填写选项"));
            }
        } else if (options.size() < 2) {
            errors.add(err(row, "选项", "选择题至少需要2个选项"));
        }
        try {
            String normalized = gradingService.normalizeStandardAnswer(type.code(), row.answer, options.keySet());
            row.normalizedAnswer = normalized;
        } catch (BizException e) {
            errors.add(err(row, "答案", e.getMessage()));
        }
        row.difficulty = difficultyOfLabel(row.difficultyLabel);
        if (row.difficulty == null) {
            errors.add(err(row, "难度", "难度必须是：易/中/难"));
        }
        row.source = sourceOfLabel(row.sourceLabel);
        if (row.source == null) {
            errors.add(err(row, "来源", "来源必须是：真题/模拟题/自编"));
        }
        if (row.nodeCodes.isBlank()) {
            errors.add(err(row, "知识点编码", "知识点编码不能为空（逗号分隔多个）"));
        }
    }

    private Map<String, SubjectQueryApi.KnowledgeNodeView> resolveNodeCodes(Long certificateId, List<ImportRow> rows) {
        Set<String> codes = new LinkedHashSet<>();
        for (ImportRow row : rows) {
            for (String code : splitCodes(row.nodeCodes)) {
                codes.add(code);
            }
        }
        Map<String, SubjectQueryApi.KnowledgeNodeView> map = new HashMap<>();
        for (SubjectQueryApi.KnowledgeNodeView node
                : subjectQueryApi.findNodesByCodes(certificateId, new ArrayList<>(codes))) {
            map.put(node.code(), node);
        }
        return map;
    }

    private void validateNodeCodes(ImportRow row, Map<String, SubjectQueryApi.KnowledgeNodeView> nodeByCode,
                                   List<ImportResultVO.ImportErrorItem> errors) {
        for (String code : splitCodes(row.nodeCodes)) {
            SubjectQueryApi.KnowledgeNodeView node = nodeByCode.get(code);
            if (node == null || node.enabled() == null || node.enabled() != 1
                    || (node.nodeType() != 2 && node.nodeType() != 3)) {
                errors.add(err(row, "知识点编码", "知识点编码不存在/未启用/非知识点类型: " + code));
            }
        }
    }

    private void detectDuplicates(Long certificateId, List<ImportRow> rows,
                                  List<ImportResultVO.ImportErrorItem> errors) {
        Set<String> seen = new LinkedHashSet<>();
        for (ImportRow row : rows) {
            if (row.stem.isBlank()) {
                continue;
            }
            if (!seen.add(row.stem)) {
                errors.add(err(row, "题干", "与文件内其他行题干重复"));
            }
        }
        if (seen.isEmpty()) {
            return;
        }
        List<Question> existing = questionMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .eq(Question::getCertificateId, certificateId)
                .in(Question::getStem, new ArrayList<>(seen)));
        Set<String> existingStems = new LinkedHashSet<>();
        for (Question q : existing) {
            existingStems.add(q.getStem());
        }
        for (ImportRow row : rows) {
            if (!row.stem.isBlank() && existingStems.contains(row.stem)) {
                errors.add(err(row, "题干", "与题库中已有题目题干重复"));
            }
        }
    }

    // ---------- 入库 ----------

    private void insertRow(Long certificateId, ImportRow row, Map<String, SubjectQueryApi.KnowledgeNodeView> nodeByCode) {
        Question question = new Question();
        question.setCertificateId(certificateId);
        question.setQuestionType(row.type.code());
        question.setStem(row.stem);
        question.setAnalysis(row.analysis);
        question.setAnswer(row.normalizedAnswer);
        question.setDifficulty(row.difficulty);
        question.setSource(row.source);
        question.setStatus(QuestionStatus.DRAFT.code());
        question.setContentVersion(1);
        question.setRejectReason("");
        questionMapper.insert(question);

        int sort = 0;
        for (Map.Entry<String, String> entry : row.options.entrySet()) {
            QuestionOption option = new QuestionOption();
            option.setQuestionId(question.getId());
            option.setOptionKey(entry.getKey());
            option.setContent(entry.getValue());
            option.setSort(sort++);
            optionMapper.insert(option);
        }
        for (String code : splitCodes(row.nodeCodes)) {
            QuestionKnowledgeNode link = new QuestionKnowledgeNode();
            link.setQuestionId(question.getId());
            link.setNodeId(nodeByCode.get(code).id());
            knowledgeNodeMapper.insert(link);
        }
    }

    // ---------- 标签映射 ----------

    private QuestionType typeOfLabel(String label) {
        return switch (label) {
            case "单选" -> QuestionType.SINGLE_CHOICE;
            case "多选" -> QuestionType.MULTIPLE_CHOICE;
            case "判断" -> QuestionType.TRUE_FALSE;
            default -> null;
        };
    }

    private Integer difficultyOfLabel(String label) {
        return switch (label) {
            case "易" -> 1;
            case "中" -> 2;
            case "难" -> 3;
            default -> null;
        };
    }

    private Integer sourceOfLabel(String label) {
        return switch (label) {
            case "真题" -> 1;
            case "模拟题" -> 2;
            case "自编" -> 3;
            default -> null;
        };
    }

    private List<String> splitCodes(String codes) {
        if (codes == null || codes.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : codes.split("[,，、;；\\s]+")) {
            if (!part.isBlank()) {
                result.add(part);
            }
        }
        return result;
    }

    private ImportResultVO.ImportErrorItem err(ImportRow row, String field, String message) {
        return new ImportResultVO.ImportErrorItem(row.rowNum, field, message);
    }

    private static class ImportRow {
        int rowNum;
        String typeLabel;
        String stem;
        String optionA;
        String optionB;
        String optionC;
        String optionD;
        String optionE;
        String optionF;
        String answer;
        String analysis;
        String difficultyLabel;
        String sourceLabel;
        String nodeCodes;
        QuestionType type;
        Integer difficulty;
        Integer source;
        String normalizedAnswer;
        Map<String, String> options = Map.of();
    }
}
