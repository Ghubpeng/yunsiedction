package com.yunsie.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunsie.module.question.api.QuestionQueryApi;
import com.yunsie.module.question.entity.Question;
import com.yunsie.module.question.entity.QuestionMistake;
import com.yunsie.module.question.enums.QuestionStatus;
import com.yunsie.module.question.mapper.QuestionMapper;
import com.yunsie.module.question.mapper.QuestionMistakeMapper;
import com.yunsie.module.sys.api.SysUserRoleApi;
import com.yunsie.module.sys.dto.RoleCreateReq;
import com.yunsie.module.sys.entity.SysPermission;
import com.yunsie.module.sys.mapper.SysPermissionMapper;
import com.yunsie.module.sys.service.SysRoleService;
import com.yunsie.module.user.dto.UserCreateReq;
import com.yunsie.module.user.service.UserService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * question 域集成测试（真实 MySQL + 完整过滤链，事务回滚不留数据）。
 * 覆盖：CRUD / 审核流 / 发布限制 / 版本重审 / 知识点关联 / 权限分离 / 错题幂等 /
 * 四种练习模式 / 下架不可练习 / Excel 导入（有效+行级错误）/ 契约 / 逻辑删除。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuestionIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private SysRoleService roleService;
    @Autowired
    private SysPermissionMapper permissionMapper;
    @Autowired
    private SysUserRoleApi sysUserRoleApi;
    @Autowired
    private QuestionMapper questionMapper;
    @Autowired
    private QuestionMistakeMapper mistakeMapper;
    @Autowired
    private QuestionQueryApi questionQueryApi;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String learnerToken;
    private Long learnerId;
    private Long certId;
    private Long kpId;
    private Long childKpId;

    @BeforeEach
    void setup() throws Exception {
        adminToken = login("admin", "Admin@123456");
        learnerId = userService.create(new UserCreateReq("it_learner_q", null, "学员", "Learner@1234", 1, null));
        learnerToken = login("it_learner_q", "Learner@1234");

        // 知识体系链：证书 → 版本(当前) → 科目 → 章节 → 知识点 → 子知识点
        long categoryId = dataId(postJson("/api/v1/certificate/categories", adminToken,
                Map.of("parentId", 0L, "name", "分类", "code", "cat-q")));
        certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "护士执业资格考试", "code", "cert-q")));
        long versionId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        long subjectId = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "专业实务", "code", "subj-q")));
        long chapterId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", 0L,
                        "nodeType", 1, "name", "基础护理", "code", "ch-q")));
        kpId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", chapterId,
                        "nodeType", 2, "name", "无菌技术", "code", "kp-q")));
        childKpId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", kpId,
                        "nodeType", 3, "name", "无菌操作原则", "code", "ckp-q")));
    }

    // ---------- 生命周期：创建→审核→发布→练习→下架 ----------

    @Test
    void fullLifecycle_createReviewPublishPracticeUnpublish() throws Exception {
        long questionId = createQuestion("生命周期题", "A", List.of(kpId));

        // 发布前练习不可见
        mockMvc.perform(get("/api/v1/question/practice/next").param("mode", "2").param("certificateId", String.valueOf(certId))
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(post("/api/v1/question/questions/{id}/submit-review", questionId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/question/questions/{id}/approve", questionId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertEquals(QuestionStatus.PUBLISHED.code(), questionMapper.selectById(questionId).getStatus());

        // 已发布 → 顺序练习可见
        mockMvc.perform(get("/api/v1/question/practice/next").param("mode", "1").param("certificateId", String.valueOf(certId))
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].answer").doesNotExist());

        // 下架后不可练习
        mockMvc.perform(post("/api/v1/question/questions/{id}/unpublish", questionId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/question/practice/next").param("mode", "1").param("certificateId", String.valueOf(certId))
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void publishedEdit_bumpsContentVersion_backToDraft() throws Exception {
        long questionId = publishQuestion("版本题");
        mockMvc.perform(put("/api/v1/question/questions/{id}", questionId).header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("stem", "版本题(改)", "analysis", "解析", "answer", "A", "difficulty", 2, "source", 2,
                                "options", List.of(Map.of("optionKey", "A", "content", "甲"), Map.of("optionKey", "B", "content", "乙"))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        Question question = questionMapper.selectById(questionId);
        assertEquals(2, question.getContentVersion());
        assertEquals(QuestionStatus.DRAFT.code(), question.getStatus());
    }

    @Test
    void deletePublished_goesRecycled() throws Exception {
        long questionId = publishQuestion("删除保护题");
        mockMvc.perform(delete("/api/v1/question/questions/{id}", questionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertEquals(QuestionStatus.RECYCLED.code(), questionMapper.selectById(questionId).getStatus());
    }

    @Test
    void deleteRecycled_listExcludesByDefault_restoreBackToDraft() throws Exception {
        long questionId = publishQuestion("回收站恢复题");
        mockMvc.perform(delete("/api/v1/question/questions/{id}", questionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        // 默认列表不含回收站；显式 status=6 可见（按本测试证书过滤，隔离 dev 库其他数据）
        mockMvc.perform(get("/api/v1/question/questions").param("certificateId", String.valueOf(certId))
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/v1/question/questions").param("certificateId", String.valueOf(certId))
                        .param("status", "6").header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        // 恢复 → 草稿
        mockMvc.perform(post("/api/v1/question/questions/{id}/restore", questionId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertEquals(QuestionStatus.DRAFT.code(), questionMapper.selectById(questionId).getStatus());
    }

    @Test
    void restore_draft_invalid_30320() throws Exception {
        long questionId = createQuestion("非回收站恢复题", "A", List.of(kpId));
        mockMvc.perform(post("/api/v1/question/questions/{id}/restore", questionId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30320));
    }

    @Test
    void deletePendingReview_blocked_30318() throws Exception {
        long questionId = createQuestion("待审核删除保护题", "A", List.of(kpId));
        mockMvc.perform(post("/api/v1/question/questions/{id}/submit-review", questionId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/v1/question/questions/{id}", questionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30318));
    }

    @Test
    void withdrawReview_backToDraft_thenDeletable() throws Exception {
        long questionId = createQuestion("撤回审核题", "A", List.of(kpId));
        mockMvc.perform(post("/api/v1/question/questions/{id}/submit-review", questionId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertEquals(QuestionStatus.PENDING_REVIEW.code(), questionMapper.selectById(questionId).getStatus());
        mockMvc.perform(post("/api/v1/question/questions/{id}/withdraw-review", questionId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertEquals(QuestionStatus.DRAFT.code(), questionMapper.selectById(questionId).getStatus());
        // 撤回后回到草稿 → 可删除
        mockMvc.perform(delete("/api/v1/question/questions/{id}", questionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void withdrawReview_draft_invalidAction_30306() throws Exception {
        long questionId = createQuestion("草稿撤回题", "A", List.of(kpId));
        mockMvc.perform(post("/api/v1/question/questions/{id}/withdraw-review", questionId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30306));
    }

    @Test
    void approveDraft_invalidAction_30306() throws Exception {
        long questionId = createQuestion("越级审核题", "A", List.of(kpId));
        mockMvc.perform(post("/api/v1/question/questions/{id}/approve", questionId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30306));
    }

    @Test
    void rejectWithReason_thenResubmit() throws Exception {
        long questionId = createQuestion("驳回题", "A", List.of(kpId));
        mockMvc.perform(post("/api/v1/question/questions/{id}/submit-review", questionId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/question/questions/{id}/reject", questionId)
                        .header(AUTH, bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "题干表述不清"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertEquals(QuestionStatus.REJECTED.code(), questionMapper.selectById(questionId).getStatus());
        mockMvc.perform(post("/api/v1/question/questions/{id}/submit-review", questionId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertEquals(QuestionStatus.PENDING_REVIEW.code(), questionMapper.selectById(questionId).getStatus());
    }

    // ---------- 校验 ----------

    @Test
    void duplicateStem_30314() throws Exception {
        createQuestion("重复题干题", "A", List.of(kpId));
        MvcResult result = postJson("/api/v1/question/questions", adminToken,
                Map.of("certificateId", certId, "questionType", 1, "stem", "重复题干题", "analysis", "解析",
                        "answer", "A", "difficulty", 2, "source", 2,
                        "options", List.of(Map.of("optionKey", "A", "content", "甲"), Map.of("optionKey", "B", "content", "乙")),
                        "nodeIds", List.of(kpId)));
        assertEquals(30314, code(result));
    }

    @Test
    void invalidNodeType_30310() throws Exception {
        long chapterId = createChapterNode("ch-invalid");
        MvcResult result = postJson("/api/v1/question/questions", adminToken,
                Map.of("certificateId", certId, "questionType", 1, "stem", "非法节点题", "analysis", "解析",
                        "answer", "A", "difficulty", 2, "source", 2,
                        "options", List.of(Map.of("optionKey", "A", "content", "甲"), Map.of("optionKey", "B", "content", "乙")),
                        "nodeIds", List.of(chapterId)));
        assertEquals(30310, code(result));
    }

    // ---------- 练习与错题 ----------

    @Test
    void practice_wrongAnswer_collectsMistake_idempotent() throws Exception {
        long questionId = publishQuestion("错题幂等题");
        mockMvc.perform(post("/api/v1/question/practice/submit").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("questionId", questionId, "answer", "B", "mode", 1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.standardAnswer").value("A"));
        mockMvc.perform(post("/api/v1/question/practice/submit").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("questionId", questionId, "answer", "B", "mode", 1))))
                .andExpect(status().isOk());

        List<QuestionMistake> mistakes = mistakeMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<QuestionMistake>()
                .eq(QuestionMistake::getUserId, learnerId));
        assertEquals(1, mistakes.size(), "重复答错不得重复建行");
        assertEquals(2, mistakes.get(0).getMistakeCount());

        // 答对不自动解决
        mockMvc.perform(post("/api/v1/question/practice/submit").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("questionId", questionId, "answer", "A", "mode", 1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.correct").value(true));
        assertEquals(1, mistakeMapper.selectById(mistakes.get(0).getId()).getStatus());

        // 手动解决
        mockMvc.perform(post("/api/v1/question/mistakes/{id}/resolve", mistakes.get(0).getId())
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertEquals(2, mistakeMapper.selectById(mistakes.get(0).getId()).getStatus());
    }

    @Test
    void practice_knowledgePointMode_includesChildNodes() throws Exception {
        long questionId = createQuestion("子知识点题", "A", List.of(childKpId));
        mockMvc.perform(post("/api/v1/question/questions/{id}/submit-review", questionId)
                        .header(AUTH, bearer(adminToken))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/question/questions/{id}/approve", questionId)
                        .header(AUTH, bearer(adminToken))).andExpect(status().isOk());

        // 知识点模式选父知识点 → 包含子知识点下的题目
        mockMvc.perform(get("/api/v1/question/practice/next").param("mode", "3").param("nodeId", String.valueOf(kpId))
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void practice_mistakeMode_onlyUnresolved() throws Exception {
        long q1 = publishQuestion("错题模式题1");
        long q2 = publishQuestion("错题模式题2");
        mockMvc.perform(post("/api/v1/question/practice/submit").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("questionId", q1, "answer", "B", "mode", 1))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/question/practice/submit").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("questionId", q2, "answer", "B", "mode", 1))))
                .andExpect(status().isOk());
        List<QuestionMistake> mistakes = mistakeMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<QuestionMistake>()
                .eq(QuestionMistake::getUserId, learnerId));
        mockMvc.perform(post("/api/v1/question/mistakes/{id}/resolve", mistakes.get(0).getId())
                        .header(AUTH, bearer(learnerToken))).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/question/practice/next").param("mode", "4").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
    }

    // ---------- 权限 ----------

    @Test
    void learner_cannotManageQuestions_403() throws Exception {
        mockMvc.perform(post("/api/v1/question/questions").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("certificateId", certId,
                                "questionType", 1, "stem", "越权", "analysis", "解析", "answer", "A",
                                "options", List.of(), "nodeIds", List.of(kpId)))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    void auditPermission_separatedFromCreate() throws Exception {
        Long createRoleId = roleService.create(new RoleCreateReq("it_q_entry", "录入员", 2, 1, "", 0));
        SysPermission createPerm = permissionMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermissionCode, "question:question:create"));
        roleService.assignPermissions(createRoleId, List.of(createPerm.getId()));
        Long entryId = userService.create(new UserCreateReq("it_q_entry_user", null, "录入", "Entry@1234", 3, null));
        sysUserRoleApi.assignRoles(entryId, List.of(createRoleId));
        String entryToken = login("it_q_entry_user", "Entry@1234");

        long questionId = createQuestion("审核分离题", "A", List.of(kpId));
        // 录入员无审核权限 → 403
        mockMvc.perform(post("/api/v1/question/questions/{id}/approve", questionId)
                        .header(AUTH, bearer(entryToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(20001));
    }

    // ---------- Excel 导入 ----------

    @Test
    void import_template_downloads() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/question/import-template").header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        String contentType = result.getResponse().getContentType();
        assertTrue(contentType != null && contentType.contains("spreadsheetml"),
                "模板下载应为 xlsx 内容类型");
        assertTrue(result.getResponse().getContentAsByteArray().length > 0);
    }

    @Test
    void import_validFile_inserts() throws Exception {
        byte[] bytes = workbookBytes(validImportRow("导入题干1", "A"));
        MockMultipartFile file = new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
        mockMvc.perform(multipart("/api/v1/question/import").file(file)
                        .param("certificateId", String.valueOf(certId))
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.successCount").value(1));
    }

    @Test
    void import_invalidRows_returnsErrors_noInsert() throws Exception {
        byte[] bytes = workbookBytes(
                new String[]{"单选", "错误导入题", "甲", "乙", "", "", "", "", "C", "解析", "易", "自编", "kp-q"},
                new String[]{"填空", "", "", "", "", "", "", "", "", "", "", "", "kp-q"});
        MockMultipartFile file = new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
        MvcResult result = mockMvc.perform(multipart("/api/v1/question/import").file(file)
                        .param("certificateId", String.valueOf(certId))
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"success\":false"));
        assertTrue(body.contains("答案"));
    }

    // ---------- 契约 / 逻辑删除 ----------

    @Test
    void questionQueryApi_contract() throws Exception {
        long questionId = publishQuestion("契约题");
        QuestionQueryApi.QuestionSnapshot snapshot = questionQueryApi.findPublishedSnapshot(questionId);
        assertNotNull(snapshot);
        assertEquals("A", snapshot.answer());
        assertEquals(2, snapshot.options().size());
        assertTrue(questionQueryApi.existsPublished(questionId));
        assertEquals(1, questionQueryApi.findPublishedQuestions(certId, null, null, 10).size());
        assertEquals(1, questionQueryApi.findPublishedQuestions(certId, null, List.of(kpId), 10).size());
        assertEquals(0, questionQueryApi.findPublishedQuestions(certId, null, List.of(childKpId), 10).size());
    }

    @Test
    void logicDelete_effective() throws Exception {
        long questionId = createQuestion("逻辑删除题", "A", List.of(kpId));
        mockMvc.perform(delete("/api/v1/question/questions/{id}", questionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        assertNull(questionMapper.selectById(questionId));
        Long raw = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_question WHERE id=? AND deleted=1", Long.class, questionId);
        assertEquals(1L, raw);
    }

    // ---------- helpers ----------

    private long createQuestion(String stem, String answer, List<Long> nodeIds) throws Exception {
        return dataId(postJson("/api/v1/question/questions", adminToken,
                Map.of("certificateId", certId, "questionType", 1, "stem", stem, "analysis", "解析",
                        "answer", answer, "difficulty", 2, "source", 2,
                        "options", List.of(Map.of("optionKey", "A", "content", "甲"), Map.of("optionKey", "B", "content", "乙")),
                        "nodeIds", nodeIds)));
    }

    private long publishQuestion(String stem) throws Exception {
        long id = createQuestion(stem, "A", List.of(kpId));
        mockMvc.perform(post("/api/v1/question/questions/{id}/submit-review", id).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/question/questions/{id}/approve", id).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        return id;
    }

    private long createChapterNode(String code) throws Exception {
        return dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", currentVersionId(), "subjectId", subjectIdOf(certId),
                        "parentId", 0L, "nodeType", 1, "name", "章节-" + code, "code", code)));
    }

    private Long currentVersionId() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/subject/versions").param("certificateId", String.valueOf(certId))
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andReturn();
        var node = objectMapper.readTree(r.getResponse().getContentAsString()).get("data");
        for (var v : node) {
            if (v.get("status").asInt() == 2) {
                return v.get("id").asLong();
            }
        }
        throw new IllegalStateException("no current version");
    }

    private Long subjectIdOf(Long certificateId) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/subject/subjects").param("certificateId", String.valueOf(certificateId))
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data").get(0).get("id").asLong();
    }

    private byte[] workbookBytes(String[]... rows) throws Exception {
        String[] headers = {"题型", "题干", "选项A", "选项B", "选项C", "选项D", "选项E", "选项F",
                "答案", "解析", "难度", "来源", "知识点编码"};
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(i + 1);
                String[] cells = rows[i];
                for (int c = 0; c < cells.length; c++) {
                    row.createCell(c).setCellValue(cells[c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String[] validImportRow(String stem, String answer) {
        return new String[]{"单选", stem, "甲选项", "乙选项", "", "", "", "", answer, "解析", "易", "自编", "kp-q"};
    }

    private MvcResult postJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(post(path).header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk()).andReturn();
    }

    private int code(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asInt();
    }

    private long dataId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    private String login(String account, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", account, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
