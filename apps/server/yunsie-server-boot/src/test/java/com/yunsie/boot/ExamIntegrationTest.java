package com.yunsie.boot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunsie.module.exam.entity.ExamAttempt;
import com.yunsie.module.exam.entity.ExamPaperQuestion;
import com.yunsie.module.exam.enums.AttemptStatus;
import com.yunsie.module.exam.mapper.ExamAttemptMapper;
import com.yunsie.module.exam.mapper.ExamPaperQuestionMapper;
import com.yunsie.module.question.api.QuestionQueryApi;
import com.yunsie.module.question.mapper.QuestionMapper;
import com.yunsie.module.sys.api.SysUserRoleApi;
import com.yunsie.module.sys.dto.RoleCreateReq;
import com.yunsie.module.sys.entity.SysPermission;
import com.yunsie.module.sys.mapper.SysPermissionMapper;
import com.yunsie.module.sys.service.SysRoleService;
import com.yunsie.module.user.dto.UserCreateReq;
import com.yunsie.module.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * exam 域集成测试（真实 MySQL + 完整过滤链，事务回滚不留数据）。
 * 核心覆盖：CRUD/组卷/发布/开始幂等/暂存 upsert/交卷幂等/成绩/权限分离/越权/
 * 快照隔离（question 修改·下架·删除后历史考试不变）/超时自动交卷/逻辑删除。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExamIntegrationTest {

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
    private ExamAttemptMapper attemptMapper;
    @Autowired
    private ExamPaperQuestionMapper paperQuestionMapper;
    @Autowired
    private QuestionQueryApi questionQueryApi;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String learnerToken;
    private Long learnerId;
    private Long otherLearnerId;
    private String otherToken;
    private Long certId;
    private Long kpId;
    private Long qSingle;
    private Long qMultiple;
    private Long qTrueFalse;

    @BeforeEach
    void setup() throws Exception {
        adminToken = login("admin", "Admin@123456");
        learnerId = userService.create(new UserCreateReq("it_exam_learner", null, "学员", "Learner@1234", 1, null));
        learnerToken = login("it_exam_learner", "Learner@1234");
        otherLearnerId = userService.create(new UserCreateReq("it_exam_other", null, "学员B", "Other@1234", 1, null));
        otherToken = login("it_exam_other", "Other@1234");

        // 知识链 + 三道已发布题目（单选A / 多选A|C / 判断T）
        long categoryId = dataId(postJson("/api/v1/certificate/categories", adminToken,
                Map.of("parentId", 0L, "name", "分类", "code", "cat-exam")));
        certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "护士执业资格考试", "code", "cert-exam")));
        long versionId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        long subjectId = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "专业实务", "code", "subj-exam")));
        long chapterId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", 0L,
                        "nodeType", 1, "name", "基础护理", "code", "ch-exam")));
        kpId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", chapterId,
                        "nodeType", 2, "name", "无菌技术", "code", "kp-exam")));

        qSingle = createPublishedQuestion("单选快照题", 1, "A", optionsA());
        qMultiple = createPublishedQuestion("多选快照题", 2, "A|C", optionsA());
        qTrueFalse = createPublishedQuestion("判断快照题", 3, "T", List.of());
    }

    // ---------- 组卷 / 发布 / available ----------

    @Test
    void fullLifecycle_assemblePublishAvailable() throws Exception {
        long examId = createExam(3, List.of(1, 2, 3));
        mockMvc.perform(post("/api/v1/exam/exams/{id}/assemble", examId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/exam/exams/{id}/publish", examId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        MvcResult avail = mockMvc.perform(get("/api/v1/exam/available").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn();
        // 可用列表含全部已发布考试（dev 库可能并存 Demo 数据，Stage 2.1），按本次考试名过滤断言
        JsonNode mine = null;
        for (JsonNode node : objectMapper.readTree(avail.getResponse().getContentAsString()).get("data")) {
            if ("模拟考试".equals(node.get("name").asText())) {
                mine = node;
            }
        }
        assertNotNull(mine, "available 列表应包含本次发布的考试");
        assertEquals(3, mine.get("questionCount").asInt());

        // 试卷快照完整（管理端含答案/解析）
        mockMvc.perform(get("/api/v1/exam/exams/{id}/paper/questions", examId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].standardAnswer").exists())
                .andExpect(jsonPath("$.data[0].sort").value(1));
    }

    @Test
    void publishWithoutPaper_30407() throws Exception {
        long examId = createExam(3, List.of(1, 2, 3));
        mockMvc.perform(post("/api/v1/exam/exams/{id}/publish", examId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30407));
    }

    @Test
    void assembleInsufficient_30419() throws Exception {
        long examId = createExam(10, List.of(1, 2, 3));
        mockMvc.perform(post("/api/v1/exam/exams/{id}/assemble", examId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30419));
    }

    @Test
    void publishedExam_cannotEdit_30403() throws Exception {
        long examId = publishExam();
        mockMvc.perform(put("/api/v1/exam/exams/{id}", examId).header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "改名", "durationMinutes", 60,
                                "rule", ruleMap(3, List.of(1, 2, 3))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30403));
    }

    @Test
    void deleteWithAttempts_30406() throws Exception {
        long examId = publishExam();
        mockMvc.perform(post("/api/v1/exam/exams/{examId}/attempts", examId).header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/exam/exams/{id}/unpublish", examId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/exam/exams/{id}", examId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30406));
    }

    // ---------- 开始 / 暂存 / 交卷 / 成绩 ----------

    @Test
    void start_idempotent_thenSaveSubmitResult() throws Exception {
        long examId = publishExam();
        MvcResult first = mockMvc.perform(post("/api/v1/exam/exams/{examId}/attempts", examId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.questions.length()").value(3))
                .andExpect(jsonPath("$.data.questions[0].standardAnswer").doesNotExist())
                .andReturn();
        long attemptId = dataIdOf(first, "attemptId");

        // 重复开始 → 同 attempt
        MvcResult second = mockMvc.perform(post("/api/v1/exam/exams/{examId}/attempts", examId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andReturn();
        assertEquals(attemptId, dataIdOf(second, "attemptId"));

        // 按题型生成正确答案（试卷随机洗牌，答案必须与题型匹配），并将最后一题改成错误答案
        List<Map<String, Object>> answerItems = correctAnswerItems(first);
        answerItems.set(answerItems.size() - 1, Map.of(
                "paperQuestionId", answerItems.get(answerItems.size() - 1).get("paperQuestionId"), "answer", "B"));
        mockMvc.perform(put("/api/v1/exam/attempts/{attemptId}/answers", attemptId)
                        .header(AUTH, bearer(learnerToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("answers", answerItems))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        // 交卷 → 2 对 1 错
        mockMvc.perform(post("/api/v1/exam/attempts/{attemptId}/submit", attemptId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.score").value(2.00))
                .andExpect(jsonPath("$.data.correctCount").value(2));

        // 重复交卷 → 同成绩（幂等）
        mockMvc.perform(post("/api/v1/exam/attempts/{attemptId}/submit", attemptId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.score").value(2.00));

        // 成绩详情（最后一题错误）
        mockMvc.perform(get("/api/v1/exam/attempts/{attemptId}/result", attemptId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].correct").value(true))
                .andExpect(jsonPath("$.data.items[2].correct").value(false))
                .andExpect(jsonPath("$.data.items[2].analysis").exists());

        // my-results
        mockMvc.perform(get("/api/v1/exam/my-results").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));

        // 已交卷再存答案 → 30415
        mockMvc.perform(put("/api/v1/exam/attempts/{attemptId}/answers", attemptId)
                        .header(AUTH, bearer(learnerToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("answers", List.of(Map.of("paperQuestionId", 1L, "answer", "B"))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30415));
    }

    @Test
    void userA_cannotReadUserBAttempt_30412() throws Exception {
        long examId = publishExam();
        MvcResult start = mockMvc.perform(post("/api/v1/exam/exams/{examId}/attempts", examId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andReturn();
        long attemptId = dataIdOf(start, "attemptId");
        mockMvc.perform(get("/api/v1/exam/attempts/{attemptId}", attemptId).header(AUTH, bearer(otherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30412));
        mockMvc.perform(get("/api/v1/exam/attempts/{attemptId}/result", attemptId).header(AUTH, bearer(otherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30412));
    }

    // ---------- 快照隔离（核心） ----------

    @Test
    void snapshotIsolation_questionChangesDoNotAffectPaper() throws Exception {
        long examId = publishExam();
        // 组卷后：改题（版本+1回草稿）、下架一题、逻辑删除一题
        mockMvc.perform(put("/api/v1/question/questions/{id}", qSingle).header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("stem", "单选快照题(已修改)", "analysis", "解析改", "answer", "B",
                                "difficulty", 2, "source", 2,
                                "options", optionsA()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/question/questions/{id}/unpublish", qMultiple)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/question/questions/{id}", qTrueFalse).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());

        // 试卷快照完全不变（试卷随机洗牌，按 questionId 过滤断言）
        mockMvc.perform(get("/api/v1/exam/exams/{id}/paper/questions", examId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[?(@.questionId==" + qSingle + ")].stem").value("单选快照题"))
                .andExpect(jsonPath("$.data[?(@.questionId==" + qSingle + ")].standardAnswer").value("A"))
                .andExpect(jsonPath("$.data[?(@.questionId==" + qSingle + ")].contentVersion").value(1))
                .andExpect(jsonPath("$.data[?(@.questionId==" + qMultiple + ")].stem").value("多选快照题"))
                .andExpect(jsonPath("$.data[?(@.questionId==" + qTrueFalse + ")].stem").value("判断快照题"));

        // 新 attempt 仍按快照正常判分（按题型全对 → 3 分）
        MvcResult start = mockMvc.perform(post("/api/v1/exam/exams/{examId}/attempts", examId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andReturn();
        long attemptId = dataIdOf(start, "attemptId");
        mockMvc.perform(put("/api/v1/exam/attempts/{attemptId}/answers", attemptId)
                        .header(AUTH, bearer(learnerToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("answers", correctAnswerItems(start)))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/exam/attempts/{attemptId}/submit", attemptId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.score").value(3.00));
    }

    // ---------- 超时自动交卷 ----------

    @Test
    void timeout_autoSubmitOnRead() throws Exception {
        long examId = publishExam();
        MvcResult start = mockMvc.perform(post("/api/v1/exam/exams/{examId}/attempts", examId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andReturn();
        long attemptId = dataIdOf(start, "attemptId");

        // 直接操作 DB 使 attempt 过期（模拟超时）
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        attempt.setExpiredAt(LocalDateTime.now().minusMinutes(1));
        attemptMapper.updateById(attempt);

        // 懒结算：读取 attempt 触发自动交卷
        mockMvc.perform(get("/api/v1/exam/attempts/{attemptId}", attemptId).header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(3));
        assertEquals(AttemptStatus.SUBMITTED.code(), attemptMapper.selectById(attemptId).getStatus());
        assertNotNull(attemptMapper.selectById(attemptId).getScore());
    }

    // ---------- 权限 ----------

    @Test
    void learner_cannotManageExams_403() throws Exception {
        mockMvc.perform(post("/api/v1/exam/exams").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("certificateId", certId, "name", "越权", "durationMinutes", 60,
                                "rule", ruleMap(1, List.of(1))))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    void readOnlyOperator_cannotCreate() throws Exception {
        Long roleId = roleService.create(new RoleCreateReq("it_exam_viewer", "考试查看", 2, 1, "", 0));
        SysPermission perm = permissionMapper.selectOne(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermissionCode, "exam:exam:read"));
        roleService.assignPermissions(roleId, List.of(perm.getId()));
        Long operatorId = userService.create(new UserCreateReq("it_exam_viewer_user", null, "查看员", "Viewer@1234", 3, null));
        sysUserRoleApi.assignRoles(operatorId, List.of(roleId));
        String token = login("it_exam_viewer_user", "Viewer@1234");

        mockMvc.perform(get("/api/v1/exam/exams").header(AUTH, bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/exam/exams").header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("certificateId", certId, "name", "越权", "durationMinutes", 60,
                                "rule", ruleMap(1, List.of(1))))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(20001));
    }

    // ---------- 契约 / 逻辑删除 ----------

    @Test
    void gradeSubmission_contractWorks() {
        assertTrue(questionQueryApi.gradeSubmission(1, "A", "a"));
        assertTrue(questionQueryApi.gradeSubmission(2, "A|C", "c,a"));
        assertFalse(questionQueryApi.gradeSubmission(3, "T", "F"));
    }

    @Test
    void logicDelete_examEffective() throws Exception {
        long examId = createExam(3, List.of(1, 2, 3));
        mockMvc.perform(delete("/api/v1/exam/exams/{id}", examId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        Long raw = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_exam WHERE id=? AND deleted=1", Long.class, examId);
        assertEquals(1L, raw);
    }

    @Test
    void noPhysicalForeignKeys_onExamTables() {
        Integer fkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                        + "WHERE CONSTRAINT_TYPE='FOREIGN KEY' AND TABLE_SCHEMA='yunsie_platform' AND TABLE_NAME IN "
                        + "('exam_exam','exam_paper','exam_paper_question','exam_paper_option','exam_attempt','exam_answer')",
                Integer.class);
        assertEquals(0, fkCount);
    }

    // ---------- helpers ----------

    private long createExam(int questionCount, List<Integer> types) throws Exception {
        return dataId(postJson("/api/v1/exam/exams", adminToken,
                Map.of("certificateId", certId, "name", "模拟考试", "durationMinutes", 60,
                        "rule", ruleMap(questionCount, types))));
    }

    private long publishExam() throws Exception {
        long examId = createExam(3, List.of(1, 2, 3));
        mockMvc.perform(post("/api/v1/exam/exams/{id}/assemble", examId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/exam/exams/{id}/publish", examId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        return examId;
    }

    private Map<String, Object> ruleMap(int questionCount, List<Integer> types) {
        return Map.of("questionCount", questionCount, "questionTypes", types);
    }

    private List<Map<String, String>> optionsA() {
        return List.of(Map.of("optionKey", "A", "content", "选项A"),
                Map.of("optionKey", "B", "content", "选项B"),
                Map.of("optionKey", "C", "content", "选项C"));
    }

    private long createPublishedQuestion(String stem, int type, String answer, List<Map<String, String>> options)
            throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "certificateId", certId, "questionType", type, "stem", stem, "analysis", "解析-" + stem,
                "answer", answer, "difficulty", 2, "source", 2,
                "options", options, "nodeIds", List.of(kpId)));
        MvcResult created = postJson("/api/v1/question/questions", adminToken, body);
        assertEquals(0, code(created), "创建题目失败: " + created.getResponse().getContentAsString());
        long id = dataId(created);
        MvcResult review = mockMvc.perform(post("/api/v1/question/questions/{id}/submit-review", id)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andReturn();
        assertEquals(0, code(review), "提审失败: " + review.getResponse().getContentAsString());
        MvcResult approve = mockMvc.perform(post("/api/v1/question/questions/{id}/approve", id)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andReturn();
        assertEquals(0, code(approve), "审核失败: " + approve.getResponse().getContentAsString());
        return id;
    }

    private int code(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asInt();
    }

    /** 从开始考试响应中按题型生成全对答案（试卷随机洗牌，答案必须匹配题型） */
    private List<Map<String, Object>> correctAnswerItems(MvcResult startResult) throws Exception {
        var questions = objectMapper.readTree(startResult.getResponse().getContentAsString())
                .get("data").get("questions");
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (var q : questions) {
            int type = q.get("questionType").asInt();
            String answer = switch (type) {
                case 1 -> "A";
                case 2 -> "A|C";
                default -> "T";
            };
            items.add(Map.of("paperQuestionId", q.get("paperQuestionId").asLong(), "answer", answer));
        }
        return items;
    }

    private long paperQuestionIdOf(long attemptId, int sort) {
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        ExamPaperQuestion pq = paperQuestionMapper.selectOne(new LambdaQueryWrapper<ExamPaperQuestion>()
                .eq(ExamPaperQuestion::getPaperId, attempt.getPaperId())
                .eq(ExamPaperQuestion::getSort, sort));
        return pq.getId();
    }

    private long dataIdOf(MvcResult result, String field) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get(field).asLong();
    }

    private long dataId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    private MvcResult postJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(post(path).header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk()).andReturn();
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
