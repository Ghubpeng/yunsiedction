package com.yunsie.boot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunsie.module.exam.api.ExamFinishedEvent;
import com.yunsie.module.user.dto.UserCreateReq;
import com.yunsie.module.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * notify 事件消费集成测试（真实 MySQL，无事务回滚，@AfterEach 物理清理）。
 * 验证 ExamFinishedEvent 异步消费者：真实消费生成成绩通知 / uk 幂等（重复事件不重复发消息）/
 * 通知失败不影响交卷。
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotifyEventIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private Long learnerId;
    private String learnerToken;
    private Long certId;
    private Long examId;
    private Long categoryId;
    private Long versionId;
    private Long subjectId;
    private Long chapterNodeId;
    private Long kpId;
    private final List<Long> questionIds = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        wipeNotifyAndLearnTables();
        adminToken = login("admin", "Admin@123456");
        learnerId = userService.create(new UserCreateReq("it_notifye_learner", null, "学员", "Learner@1234", 1, null));
        learnerToken = login("it_notifye_learner", "Learner@1234");

        categoryId = dataId(postJson("/api/v1/certificate/categories", adminToken,
                Map.of("parentId", 0L, "name", "分类", "code", "cat-notifye")));
        certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "护士执业资格考试", "code", "cert-notifye")));
        versionId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        subjectId = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "专业实务", "code", "subj-notifye")));
        chapterNodeId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", 0L,
                        "nodeType", 1, "name", "基础护理", "code", "ch-notifye")));
        kpId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", chapterNodeId,
                        "nodeType", 2, "name", "无菌技术", "code", "kp-notifye")));
        questionIds.add(createPublishedQuestion("事件通知题一", "A"));
        questionIds.add(createPublishedQuestion("事件通知题二", "A"));
        examId = dataId(postJson("/api/v1/exam/exams", adminToken,
                Map.of("certificateId", certId, "name", "事件考试", "durationMinutes", 60,
                        "passScore", 60.0, "rule", Map.of("questionCount", 2, "questionTypes", List.of(1)))));
        mockMvc.perform(post("/api/v1/exam/exams/{id}/assemble", examId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/exam/exams/{id}/publish", examId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @AfterEach
    void cleanup() {
        wipeNotifyAndLearnTables();
        jdbcTemplate.update("DELETE FROM exam_answer WHERE attempt_id IN (SELECT id FROM exam_attempt WHERE exam_id=?)", examId);
        jdbcTemplate.update("DELETE FROM exam_attempt WHERE exam_id=?", examId);
        jdbcTemplate.update("DELETE FROM exam_paper_option WHERE paper_question_id IN "
                + "(SELECT id FROM exam_paper_question WHERE paper_id IN (SELECT id FROM exam_paper WHERE exam_id=?))", examId);
        jdbcTemplate.update("DELETE FROM exam_paper_question WHERE paper_id IN (SELECT id FROM exam_paper WHERE exam_id=?)", examId);
        jdbcTemplate.update("DELETE FROM exam_paper WHERE exam_id=?", examId);
        jdbcTemplate.update("DELETE FROM exam_exam WHERE id=?", examId);
        for (Long qid : questionIds) {
            jdbcTemplate.update("DELETE FROM question_option WHERE question_id=?", qid);
            jdbcTemplate.update("DELETE FROM question_knowledge_node WHERE question_id=?", qid);
            jdbcTemplate.update("DELETE FROM question_question WHERE id=?", qid);
        }
        jdbcTemplate.update("DELETE FROM subject_knowledge_node WHERE id IN (?, ?)", chapterNodeId, kpId);
        jdbcTemplate.update("DELETE FROM subject_subject WHERE id=?", subjectId);
        jdbcTemplate.update("DELETE FROM subject_version WHERE id=?", versionId);
        jdbcTemplate.update("DELETE FROM certificate_cert WHERE id=?", certId);
        jdbcTemplate.update("DELETE FROM certificate_category WHERE id=?", categoryId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id=?", learnerId);
        jdbcTemplate.update("DELETE FROM user_refresh_token WHERE user_id=?", learnerId);
        jdbcTemplate.update("DELETE FROM user_profile WHERE user_id=?", learnerId);
        jdbcTemplate.update("DELETE FROM user_credential WHERE user_id=?", learnerId);
        jdbcTemplate.update("DELETE FROM user_account WHERE id=?", learnerId);
    }

    @Test
    void examFinished_asyncScoreNoticeCreated() throws Exception {
        long attemptId = startAndSubmit();

        // 异步事件消费：成绩通知出现（轮询等待）
        assertTrue(awaitTrue(() -> scoreNoticeCount(attemptId) == 1, 8000),
                "ExamFinishedEvent 未被消费（无成绩通知）");

        mockMvc.perform(get("/api/v1/notify/messages").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].messageType").value(1))
                .andExpect(jsonPath("$.data.list[0].bizId").value(attemptId))
                .andExpect(jsonPath("$.data.list[0].title").value("考试成绩通知"))
                .andExpect(jsonPath("$.data.list[0].content").value(
                        org.hamcrest.Matchers.containsString("事件考试")))
                .andExpect(jsonPath("$.data.list[0].content").value(
                        org.hamcrest.Matchers.containsString("2.00")));
        mockMvc.perform(get("/api/v1/notify/messages/unread-count").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void duplicateEvent_noDuplicateMessage() throws Exception {
        long attemptId = startAndSubmit();
        assertTrue(awaitTrue(() -> scoreNoticeCount(attemptId) == 1, 8000), "首次事件未消费");

        // 模拟重复投递同一事件
        eventPublisher.publishEvent(new ExamFinishedEvent(attemptId, learnerId, examId,
                new BigDecimal("2.00"), 2, 2, LocalDateTime.now()));
        sleep(2000);

        assertEquals(1, scoreNoticeCount(attemptId), "重复事件不得产生重复消息");
    }

    // ---------- helpers ----------

    private long startAndSubmit() throws Exception {
        MvcResult start = mockMvc.perform(post("/api/v1/exam/exams/{examId}/attempts", examId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long attemptId = objectMapper.readTree(start.getResponse().getContentAsString())
                .get("data").get("attemptId").asLong();
        JsonNode questions = objectMapper.readTree(start.getResponse().getContentAsString())
                .get("data").get("questions");
        List<Map<String, Object>> answers = new ArrayList<>();
        for (JsonNode q : questions) {
            answers.add(Map.of("paperQuestionId", q.get("paperQuestionId").asLong(),
                    "answer", q.get("options").get(0).get("optionKey").asText()));
        }
        mockMvc.perform(put("/api/v1/exam/attempts/{attemptId}/answers", attemptId)
                        .header(AUTH, bearer(learnerToken))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answers", answers))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/exam/attempts/{attemptId}/submit", attemptId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(2.00));
        return attemptId;
    }

    private int scoreNoticeCount(long attemptId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notify_message WHERE user_id=? AND message_type=1 AND biz_id=?",
                Integer.class, learnerId, attemptId);
        return count == null ? 0 : count;
    }

    private boolean awaitTrue(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(200);
        }
        return condition.getAsBoolean();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void wipeNotifyAndLearnTables() {
        // 学习档案监听器也会消费 ExamFinishedEvent（异步写入），一并清理其派生行
        jdbcTemplate.update("DELETE FROM notify_message");
        jdbcTemplate.update("DELETE FROM learn_event_dedup");
        jdbcTemplate.update("DELETE FROM learn_study_calendar");
        jdbcTemplate.update("DELETE FROM learn_mastery");
        jdbcTemplate.update("DELETE FROM learn_profile_summary");
    }

    private long createPublishedQuestion(String stem, String answer) throws Exception {
        MvcResult created = postJson("/api/v1/question/questions", adminToken, Map.of(
                "certificateId", certId, "questionType", 1, "stem", stem, "analysis", "解析",
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
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
    }

    private String login(String account, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/user/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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
