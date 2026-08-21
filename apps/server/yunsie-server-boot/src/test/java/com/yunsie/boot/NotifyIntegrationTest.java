package com.yunsie.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunsie.module.exam.api.ExamQueryApi;
import com.yunsie.module.notify.mapper.NotifyMessageMapper;
import com.yunsie.module.notify.service.ExamReminderScanner;
import com.yunsie.module.notify.service.NotifyMessageService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * notify 集成测试（真实 MySQL + 完整过滤链，事务回滚不留数据）。
 * 覆盖：消息 CRUD/已读/未读数/本人隔离/越权 30701/开考提醒扫描（直接调用）/
 * 定向历史学员/uk 去重/traceId/V9 表结构。
 * （考试事件异步链路在 NotifyEventIntegrationTest 单独验证）
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotifyIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private NotifyMessageService messageService;
    @Autowired
    private NotifyMessageMapper messageMapper;
    @Autowired
    private ExamReminderScanner reminderScanner;
    @Autowired
    private ExamQueryApi examQueryApi;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private Long learnerId;
    private String learnerToken;
    private Long otherId;
    private String otherToken;
    private Long certId;
    private Long examHistoryId;
    private Long examOpeningId;

    @BeforeEach
    void setup() throws Exception {
        adminToken = login("admin", "Admin@123456");
        learnerId = userService.create(new UserCreateReq("it_notify_learner", null, "学员", "Learner@1234", 1, null));
        learnerToken = login("it_notify_learner", "Learner@1234");
        otherId = userService.create(new UserCreateReq("it_notify_other", null, "学员B", "Other@1234", 1, null));
        otherToken = login("it_notify_other", "Other@1234");

        long categoryId = dataId(postJson("/api/v1/certificate/categories", adminToken,
                Map.of("parentId", 0L, "name", "分类", "code", "cat-notify")));
        certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "护士执业资格考试", "code", "cert-notify")));
        long versionId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        long subjectId = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "专业实务", "code", "subj-notify")));
        long chapterId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", 0L,
                        "nodeType", 1, "name", "基础护理", "code", "ch-notify")));
        long kpId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", chapterId,
                        "nodeType", 2, "name", "无菌技术", "code", "kp-notify")));
        createPublishedQuestion(kpId, "通知题一");
        createPublishedQuestion(kpId, "通知题二");

        examHistoryId = publishExam("历史考试");
        examOpeningId = publishExam("即将开考", LocalDateTime.now().plusHours(1));
    }

    // ---------- 消息 CRUD / 已读 / 隔离 ----------

    @Test
    void messageCrud_readAndIsolation() throws Exception {
        // 学员一条消息（直接服务写入，测试事务内）
        messageService.createExamReminder(learnerId,
                new ExamQueryApi.OpeningExamView(examOpeningId, certId, "即将开考",
                        LocalDateTime.now().plusHours(1)));
        Long messageId = messageMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                        com.yunsie.module.notify.entity.NotifyMessage>()
                .eq(com.yunsie.module.notify.entity.NotifyMessage::getUserId, learnerId)
                .eq(com.yunsie.module.notify.entity.NotifyMessage::getMessageType, 2)
                .eq(com.yunsie.module.notify.entity.NotifyMessage::getBizId, examOpeningId))
                .getId();

        // 本人列表 + 未读数
        mockMvc.perform(get("/api/v1/notify/messages").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(messageId))
                .andExpect(jsonPath("$.data.list[0].messageType").value(2))
                .andExpect(jsonPath("$.data.list[0].isRead").value(0));
        mockMvc.perform(get("/api/v1/notify/messages/unread-count").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(1));

        // 他人列表为空（严格本人隔离）
        mockMvc.perform(get("/api/v1/notify/messages").header(AUTH, bearer(otherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));

        // 他人读/删本人消息 → 30701
        mockMvc.perform(put("/api/v1/notify/messages/{id}/read", messageId).header(AUTH, bearer(otherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30701));
        mockMvc.perform(delete("/api/v1/notify/messages/{id}", messageId).header(AUTH, bearer(otherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30701));

        // 本人标记已读 → 未读数 0 → 重复标记幂等
        mockMvc.perform(put("/api/v1/notify/messages/{id}/read", messageId).header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(put("/api/v1/notify/messages/{id}/read", messageId).header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/notify/messages/unread-count").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(0));

        // 再来一条 → 全部已读
        messageService.createExamReminder(learnerId,
                new ExamQueryApi.OpeningExamView(examHistoryId, certId, "历史考试", LocalDateTime.now().plusHours(2)));
        mockMvc.perform(put("/api/v1/notify/messages/read-all").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/notify/messages/unread-count").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(0));

        // 本人删除 → 列表 1 条
        mockMvc.perform(delete("/api/v1/notify/messages/{id}", messageId).header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/notify/messages").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }

    // ---------- 开考提醒扫描 ----------

    @Test
    void reminderScanner_targetsOnlyExamHistoryLearners() {
        // 学员有历史交卷记录（直接落库，避免触发异步事件污染测试事务）
        insertSubmittedAttempt(learnerId, examHistoryId);

        reminderScanner.scan();

        // 学员收到开考提醒（examOpeningId）；无历史学员（otherId）不收
        Integer learnerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notify_message WHERE user_id=? AND message_type=2 AND biz_id=?",
                Integer.class, learnerId, examOpeningId);
        assertEquals(1, learnerCount);
        Integer otherCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notify_message WHERE user_id=? AND message_type=2 AND biz_id=?",
                Integer.class, otherId, examOpeningId);
        assertEquals(0, otherCount);

        // 再次扫描 → uk 幂等，仍 1 条
        reminderScanner.scan();
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notify_message WHERE user_id=? AND message_type=2 AND biz_id=?",
                Integer.class, learnerId, examOpeningId));
    }

    // ---------- 契约 / traceId / V9 ----------

    @Test
    void examQueryApi_openingExams_contract() {
        List<ExamQueryApi.OpeningExamView> exams = examQueryApi.listExamsOpeningSoon(
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(24));
        // examHistoryId valid_from=null 不返回；examOpeningId 在窗口内返回
        assertEquals(1, exams.size());
        assertEquals(examOpeningId, exams.get(0).id());
    }

    @Test
    void traceId_headerEchoedInBody() throws Exception {
        mockMvc.perform(get("/api/v1/notify/messages").header(AUTH, bearer(learnerToken))
                        .header("X-Trace-Id", "it-notify-trace-01"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "it-notify-trace-01"))
                .andExpect(jsonPath("$.traceId").value("it-notify-trace-01"));
    }

    @Test
    void v9Table_noPhysicalForeignKeys() {
        Integer fkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                        + "WHERE CONSTRAINT_TYPE='FOREIGN KEY' AND TABLE_SCHEMA='yunsie_platform' "
                        + "AND TABLE_NAME='notify_message'", Integer.class);
        assertEquals(0, fkCount);
    }

    // ---------- helpers ----------

    private long publishExam(String name) throws Exception {
        return publishExam(name, null);
    }

    private long publishExam(String name, LocalDateTime validFrom) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "certificateId", certId, "name", name, "durationMinutes", 60,
                "passScore", 60.0, "rule", Map.of("questionCount", 2, "questionTypes", List.of(1))));
        if (validFrom != null) {
            body.put("validFrom", validFrom.toString());
        }
        long examId = dataId(postJson("/api/v1/exam/exams", adminToken, body));
        mockMvc.perform(post("/api/v1/exam/exams/{id}/assemble", examId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/exam/exams/{id}/publish", examId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        return examId;
    }

    /** 直接写入已交卷 attempt（仅测试用：模拟考试历史，避免走交卷 API 触发异步事件） */
    private void insertSubmittedAttempt(Long userId, Long examId) {
        jdbcTemplate.update("INSERT INTO exam_attempt (exam_id, paper_id, user_id, status, started_at, "
                        + "expired_at, submitted_at, score, correct_count, question_count) "
                        + "SELECT ?, id, ?, 3, NOW(), DATE_ADD(NOW(), INTERVAL 1 HOUR), NOW(), 60.00, 1, 2 "
                        + "FROM exam_paper WHERE exam_id=? LIMIT 1",
                examId, userId, examId);
    }

    private long createPublishedQuestion(long kpId, String stem) throws Exception {
        MvcResult created = postJson("/api/v1/question/questions", adminToken, Map.of(
                "certificateId", certId, "questionType", 1, "stem", stem, "analysis", "解析",
                "answer", "A", "difficulty", 2, "source", 2,
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
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
    }

    private String login(String account, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("account", account, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
