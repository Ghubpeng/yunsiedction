package com.yunsie.module.question.service.impl;

import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.question.entity.Question;
import com.yunsie.module.question.error.QuestionErrorCode;
import com.yunsie.module.question.mapper.QuestionKnowledgeNodeMapper;
import com.yunsie.module.question.mapper.QuestionMapper;
import com.yunsie.module.question.mapper.QuestionOptionMapper;
import com.yunsie.module.question.service.GradingService;
import com.yunsie.module.question.vo.ImportResultVO;
import com.yunsie.module.subject.api.SubjectQueryApi;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Excel 导入单元测试：表头 / 行数上限 / 行级错误 / 整体不落库 / 正常入库。
 */
class ExcelImportServiceTest {

    private static final String[] HEADERS = {
            "题型", "题干", "选项A", "选项B", "选项C", "选项D", "选项E", "选项F",
            "答案", "解析", "难度", "来源", "知识点编码"};

    private ExcelImportServiceImpl service(QuestionMapper questionMapper, SubjectQueryApi subjectQueryApi,
                                           CertificateQueryApi certificateQueryApi) {
        return new ExcelImportServiceImpl(questionMapper, mock(QuestionOptionMapper.class),
                mock(QuestionKnowledgeNodeMapper.class), new GradingService(), subjectQueryApi, certificateQueryApi);
    }

    private CertificateQueryApi certApi() {
        CertificateQueryApi api = mock(CertificateQueryApi.class);
        when(api.findCertificate(100L))
                .thenReturn(new CertificateQueryApi.CertificateView(100L, 1L, "护士执业资格", "nurse", "", 1));
        return api;
    }

    private SubjectQueryApi nodeApi() {
        SubjectQueryApi api = mock(SubjectQueryApi.class);
        when(api.findNodesByCodes(any(), any())).thenReturn(List.of(
                new SubjectQueryApi.KnowledgeNodeView(200L, 1L, 100L, 1L, 2, "无菌技术", "KP1", "/1/200/", 2, 1)));
        return api;
    }

    private byte[] workbookWith(List<String[]> dataRows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            for (int i = 0; i < dataRows.size(); i++) {
                Row row = sheet.createRow(i + 1);
                String[] cells = dataRows.get(i);
                for (int c = 0; c < cells.length; c++) {
                    row.createCell(c).setCellValue(cells[c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String[] validRow(String stem, String answer) {
        return new String[]{"单选", stem, "选项A", "选项B", "", "", "", "", answer, "解析", "易", "自编", "KP1"};
    }

    @Test
    void invalidHeader_30317() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet().createRow(0).createCell(0).setCellValue("错误表头");
            workbook.write(out);
            BizException ex = assertThrows(BizException.class,
                    () -> service(mock(QuestionMapper.class), nodeApi(), certApi())
                            .importFile(out.toByteArray(), "a.xlsx", 100L));
            assertEquals(QuestionErrorCode.IMPORT_HEADER_INVALID.code(), ex.getCode());
        }
    }

    @Test
    void wrongExtension_30315() {
        BizException ex = assertThrows(BizException.class,
                () -> service(mock(QuestionMapper.class), nodeApi(), certApi())
                        .importFile(new byte[]{1}, "a.xls", 100L));
        assertEquals(QuestionErrorCode.IMPORT_FILE_INVALID.code(), ex.getCode());
    }

    @Test
    void rowErrors_collected_andNothingInserted() throws IOException {
        // 行1：知识点编码错误；行2：题型非法
        byte[] bytes = workbookWith(List.of(
                new String[]{"单选", "题干1", "选项A", "选项B", "", "", "", "", "A", "解析", "易", "自编", "BAD_CODE"},
                new String[]{"填空", "题干2", "", "", "", "", "", "", "x", "解析", "易", "自编", "KP1"}));
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        when(questionMapper.selectList(any())).thenReturn(List.of());

        ImportResultVO result = service(questionMapper, nodeApi(), certApi()).importFile(bytes, "a.xlsx", 100L);

        assertFalse(result.success());
        assertEquals(2, result.total());
        assertEquals(0, result.successCount());
        assertFalse(result.errors().isEmpty());
        verify(questionMapper, never()).insert(any(Question.class));
    }

    @Test
    void validRows_inserted() throws IOException {
        byte[] bytes = workbookWith(List.of(
                validRow("题干A", "A"),
                validRow("题干B", "B")));
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        when(questionMapper.selectList(any())).thenReturn(List.of());

        ImportResultVO result = service(questionMapper, nodeApi(), certApi()).importFile(bytes, "a.xlsx", 100L);

        assertTrue(result.success());
        assertEquals(2, result.successCount());
        verify(questionMapper, times(2)).insert(any(Question.class));
    }

    @Test
    void duplicateStemWithinFile_fails() throws IOException {
        byte[] bytes = workbookWith(List.of(validRow("重复题干", "A"), validRow("重复题干", "B")));
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        when(questionMapper.selectList(any())).thenReturn(List.of());

        ImportResultVO result = service(questionMapper, nodeApi(), certApi()).importFile(bytes, "a.xlsx", 100L);

        assertFalse(result.success());
        verify(questionMapper, never()).insert(any(Question.class));
    }
}
