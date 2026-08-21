package com.yunsie.boot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * learning-profile 集成测试（真实 MySQL + 完整过滤链，事务回滚不留数据）。
 * 覆盖：练习→档案 / 课程进度→档案 / 掌握度 / 薄弱点 / 日历 / 预测 /
 * 本人隔离 / 教师 DataScope / 权限 403 / 管理员重算 / traceId / V8 表结构。
 * （考试事件消费的异步链路在 LearningProfileEventIntegrationTest 单独验证）
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LearningProfileIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private Long learnerId;
    private String learnerToken;
    private Long teacherAId;
    private String teacherAToken;
    private Long teacherBId;
    private String teacherBToken;
    private Long certId;
    private Long kpId;
    private Long questionId;
    private Long lessonId;
    private Long courseId;
    private Long examId;

    @BeforeEach
    void setup() throws Exception {
        wipeLearnTables();
        adminToken = login("admin", "Admin@123456");
        learnerId = userService.create(new UserCreateReq("it_lp_learner", null, "学员", "Learner@1234", 1, null));
        learnerToken = login("it_lp_learner", "Learner@1234");
        teacherAId = userService.create(new UserCreateReq("it_lp_teacher_a", null, "教师A", "TeacherA@1234", 2, null));
        teacherAToken = login("it_lp_teacher_a", "TeacherA@1234");
        teacherBId = userService.create(new UserCreateReq("it_lp_teacher_b", null, "教师B", "TeacherB@1234", 2, null));
        teacherBToken = login("it_lp_teacher_b", "TeacherB@1234");
        // 教师授予 learn:profile:view
        Long roleId = roleService.create(new RoleCreateReq("it_lp_teacher_role", "档案教师", 2, 1, "", 0));
        SysPermission viewPerm = permissionMapper.selectOne(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermissionCode, "learn:profile:view"));
        roleService.assignPermissions(roleId, List.of(viewPerm.getId()));
        sysUserRoleApi.assignRoles(teacherAId, List.of(roleId));
        sysUserRoleApi.assignRoles(teacherBId, List.of(roleId));

        // 知识链：证书/版本/科目/章/知识点
        long categoryId = dataId(postJson("/api/v1/certificate/categories", adminToken,
                Map.of("parentId", 0L, "name", "分类", "code", "cat-lp")));
        certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "护士执业资格考试", "code", "cert-lp")));
        long versionId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        long subjectId = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "专业实务", "code", "subj-lp")));
        long chapterId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", 0L,
                        "nodeType", 1, "name", "基础护理", "code", "ch-lp")));
        kpId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", chapterId,
                        "nodeType", 2, "name", "无菌技术", "code", "kp-lp")));

        // 已发布题目（单选）
        questionId = createPublishedQuestion("单选题", 1, "A");
        // 已发布课程（teacherA 名下，admin 代建）
        courseId = dataId(postJson("/api/v1/course/courses", adminToken,
                Map.of("certificateId", certId, "teacherId", teacherAId, "title", "课程", "description", "")));
        long courseChapter = dataId(postJson("/api/v1/course/chapters", adminToken,
                Map.of("courseId", courseId, "title", "章节", "sort", 0)));
        lessonId = dataId(postJson("/api/v1/course/lessons", adminToken,
                Map.of("chapterId", courseChapter, "title", "小节", "durationSeconds", 600, "sort", 0)));
        mockMvc.perform(post("/api/v1/course/courses/{id}/publish", courseId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        // 考试（预测用，无需发布）
        examId = dataId(postJson("/api/v1/exam/exams", adminToken,
                Map.of("certificateId", certId, "name", "模拟考试", "durationMinutes", 60,
                        "passScore", 60.0, "rule", Map.of("questionCount", 1, "questionTypes", List.of(1)))));
    }

    // ---------- 练习 → 档案 ----------

    @Test
    void practice_updatesSummaryAndMastery() throws Exception {
        // 1 对 1 错（答题耗时 1500/2000ms）
        submitPractice("A", 1500);
        submitPractice("B", 2000);

        mockMvc.perform(get("/api/v1/learn/me/summary").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.practiceCount").value(2))
                .andExpect(jsonPath("$.data.practiceCorrectCount").value(1))
                .andExpect(jsonPath("$.data.totalStudySeconds").value(3))
                .andExpect(jsonPath("$.data.lastStudyDate").value(LocalDate.now().toString()));

        // 掌握度：节点出现（版本隔离当前版本），1 对 1 错 → 12-18 → clamp 0
        mockMvc.perform(get("/api/v1/learn/me/mastery").param("certificateId", String.valueOf(certId))
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].nodeId").value(kpId))
                .andExpect(jsonPath("$.data[0].correctCount").value(1))
                .andExpect(jsonPath("$.data[0].wrongCount").value(1))
                .andExpect(jsonPath("$.data[0].masteryValue").value(0));

        // 薄弱点：包含该节点
        mockMvc.perform(get("/api/v1/learn/me/weakness").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].nodeId").value(kpId))
                .andExpect(jsonPath("$.data[0].wrongCount").value(2));

        // 日历：今天练习 2 次
        mockMvc.perform(get("/api/v1/learn/me/calendar").param("month", YearMonth.now().toString())
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.days[0].practiceCount").value(2))
                .andExpect(jsonPath("$.data.days[0].studySeconds").value(3))
                .andExpect(jsonPath("$.data.currentStreak").value(1));
    }

    // ---------- 课程进度 → 档案 ----------

    @Test
    void courseProgress_updatesSummary() throws Exception {
        mockMvc.perform(post("/api/v1/course/progress").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lessonId", lessonId, "positionSeconds", 570))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/learn/me/summary").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.courseFinishedLessons").value(1))
                .andExpect(jsonPath("$.data.totalStudySeconds").value(570))
                .andExpect(jsonPath("$.data.lastStudyDate").value(LocalDate.now().toString()));
    }

    // ---------- 本人隔离 ----------

    @Test
    void mastery_withoutCertificate_paramOptional() throws Exception {
        // Stage 1.8 追加：用户端无证书上下文时省略 certificateId → 返回全部证书当前版本
        submitPractice("A", 1500);
        mockMvc.perform(get("/api/v1/learn/me/mastery").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].nodeId").value(kpId));
        // 带 certificateId 的既有行为不变
        mockMvc.perform(get("/api/v1/learn/me/mastery").param("certificateId", String.valueOf(certId))
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void me_endpoints_strictSelfIsolation() throws Exception {
        submitPractice("A", 1500);
        // 学员 A 有 1 次练习
        mockMvc.perform(get("/api/v1/learn/me/summary").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.practiceCount").value(1))
                .andExpect(jsonPath("$.data.userId").value(learnerId));
        // 教师 B 的 /me 与学员 A 无关
        mockMvc.perform(get("/api/v1/learn/me/summary").header(AUTH, bearer(teacherBToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.practiceCount").value(0));
        // /me 路径无 userId 入参（越权构造 userId 参数无效）
        mockMvc.perform(get("/api/v1/learn/me/summary").param("userId", String.valueOf(learnerId))
                        .header(AUTH, bearer(teacherBToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.practiceCount").value(0));
    }

    // ---------- 教师 DataScope ----------

    @Test
    void teacherDataScope_courseScopedOnly() throws Exception {
        // 学员在 teacherA 的课程学习
        mockMvc.perform(post("/api/v1/course/progress").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lessonId", lessonId, "positionSeconds", 100))))
                .andExpect(status().isOk());

        // teacherA：可见自己课程学员列表
        mockMvc.perform(get("/api/v1/learn/students").param("courseId", String.valueOf(courseId))
                        .header(AUTH, bearer(teacherAToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(learnerId));
        // teacherB：跨课程 → 30602
        mockMvc.perform(get("/api/v1/learn/students").param("courseId", String.valueOf(courseId))
                        .header(AUTH, bearer(teacherBToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30602));
        // teacherA：学员详情可见
        mockMvc.perform(get("/api/v1/learn/students/{id}", learnerId).header(AUTH, bearer(teacherAToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.summary.practiceCount").value(0));
        // teacherB：学员详情 → 30602
        mockMvc.perform(get("/api/v1/learn/students/{id}", learnerId).header(AUTH, bearer(teacherBToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30602));
        // 管理员 hasAllData 覆盖
        mockMvc.perform(get("/api/v1/learn/students").param("courseId", String.valueOf(courseId))
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void learner_withoutViewPerm_teacherEndpoint_403() throws Exception {
        mockMvc.perform(get("/api/v1/learn/students").param("courseId", String.valueOf(courseId))
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(20001));
        mockMvc.perform(get("/api/v1/learn/students/{id}", learnerId).header(AUTH, bearer(learnerToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(20001));
    }

    // ---------- 管理员重算 ----------

    @Test
    void adminRecalc_idempotent_andPermitted() throws Exception {
        submitPractice("A", 1500);
        mockMvc.perform(post("/api/v1/learn/admin/recalc/{userId}", learnerId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.practiceCount").value(1));
        // 重算幂等：再次重算结果一致
        mockMvc.perform(post("/api/v1/learn/admin/recalc/{userId}", learnerId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.practiceCount").value(1));
        // 学员无权限 → 403
        mockMvc.perform(post("/api/v1/learn/admin/recalc/{userId}", learnerId).header(AUTH, bearer(learnerToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(20001));
    }

    // ---------- 预测 / 参数校验 ----------

    @Test
    void prediction_ruleBased() throws Exception {
        mockMvc.perform(get("/api/v1/learn/me/prediction").param("examId", String.valueOf(examId))
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.examId").value(examId))
                .andExpect(jsonPath("$.data.ruleVersion").value("v1.0"))
                .andExpect(jsonPath("$.data.probability").isNumber())
                .andExpect(jsonPath("$.data.basis").isNotEmpty());
        mockMvc.perform(get("/api/v1/learn/me/prediction").param("examId", "999999")
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30605));
    }

    @Test
    void calendar_invalidMonth_30604() throws Exception {
        mockMvc.perform(get("/api/v1/learn/me/calendar").param("month", "2026-13")
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30604));
    }

    // ---------- traceId ----------

    @Test
    void traceId_headerEchoedInBody() throws Exception {
        mockMvc.perform(get("/api/v1/learn/me/summary").header(AUTH, bearer(learnerToken))
                        .header("X-Trace-Id", "it-lp-trace-0001"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "it-lp-trace-0001"))
                .andExpect(jsonPath("$.traceId").value("it-lp-trace-0001"));
    }

    // ---------- V8 表结构 ----------

    @Test
    void v8Tables_noPhysicalForeignKeys() {
        Integer fkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                        + "WHERE CONSTRAINT_TYPE='FOREIGN KEY' AND TABLE_SCHEMA='yunsie_platform' AND TABLE_NAME IN "
                        + "('learn_profile_summary','learn_mastery','learn_study_calendar','learn_event_dedup','learn_config','learn_user_goal')",
                Integer.class);
        assertEquals(0, fkCount);
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='yunsie_platform' "
                        + "AND TABLE_NAME LIKE 'learn\\_%'", Integer.class);
        // V8 五表 + V10 learn_user_goal（用户考试目标，CONFLICTS #26）
        assertEquals(6, tableCount);
        // 权限点种子
        Integer permCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_permission WHERE deleted=0 AND permission_code LIKE 'learn:%'", Integer.class);
        assertEquals(3, permCount);
    }

    // ---------- helpers ----------

    private void wipeLearnTables() {
        jdbcTemplate.update("DELETE FROM learn_event_dedup");
        jdbcTemplate.update("DELETE FROM learn_study_calendar");
        jdbcTemplate.update("DELETE FROM learn_mastery");
        jdbcTemplate.update("DELETE FROM learn_profile_summary");
    }

    private void submitPractice(String answer, int responseTimeMs) throws Exception {
        mockMvc.perform(post("/api/v1/question/practice/submit").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("questionId", questionId, "answer", answer, "mode", 3,
                                "nodeId", kpId, "responseTimeMs", responseTimeMs))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    private long createPublishedQuestion(String stem, int type, String answer) throws Exception {
        MvcResult created = postJson("/api/v1/question/questions", adminToken, Map.of(
                "certificateId", certId, "questionType", type, "stem", stem, "analysis", "解析",
                "answer", answer, "difficulty", 2, "source", 2,
                "options", List.of(Map.of("optionKey", "A", "content", "选项A"),
                        Map.of("optionKey", "B", "content", "选项B")),
                "nodeIds", List.of(kpId)));
        long id = dataId(created);
        mockMvc.perform(post("/api/v1/question/questions/{id}/submit-review", id).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/question/questions/{id}/approve", id).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        return id;
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
