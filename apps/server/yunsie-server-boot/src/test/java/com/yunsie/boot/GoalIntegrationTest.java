package com.yunsie.boot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunsie.module.question.entity.QuestionPracticeRecord;
import com.yunsie.module.question.mapper.QuestionPracticeRecordMapper;
import com.yunsie.module.user.dto.UserCreateReq;
import com.yunsie.module.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户考试目标集成测试（Stage 2.1 学习产品化）。
 * 覆盖：无目标 current null / select 建立目标（含名称）/ 重复 select 幂等 /
 * 切换科目状态流转（历史保留）/ 切换后学习数据不删。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GoalIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private QuestionPracticeRecordMapper practiceRecordMapper;

    private String adminToken;
    private String learnerToken;
    private Long learnerId;
    private Long certId;
    private Long subject1;
    private Long subject2;

    @BeforeEach
    void setup() throws Exception {
        adminToken = login("admin", "Admin@123456");
        learnerId = userService.create(new UserCreateReq("it_goal_learner", null, "学员", "Learner@1234", 1, null));
        learnerToken = login("it_goal_learner", "Learner@1234");

        long categoryId = dataId(postJson("/api/v1/certificate/categories", adminToken,
                Map.of("parentId", 0L, "name", "分类", "code", "cat-goal")));
        certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "护士执业资格考试", "code", "cert-goal")));
        long versionId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        subject1 = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "专业实务", "code", "subj-goal-1")));
        subject2 = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "实践能力", "code", "subj-goal-2")));
    }

    @Test
    void noGoal_currentNull() throws Exception {
        mockMvc.perform(get("/api/v1/learn/goals/current").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void select_establishesGoal_withNames() throws Exception {
        mockMvc.perform(post("/api/v1/learn/goals/select").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("certificateId", certId, "subjectId", subject1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/learn/goals/current").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.certificateId").value(certId))
                .andExpect(jsonPath("$.data.subjectId").value(subject1))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.certificateName").isNotEmpty())
                .andExpect(jsonPath("$.data.subjectName").value("专业实务"));
    }

    @Test
    void reselect_sameGoal_idempotent() throws Exception {
        mockMvc.perform(post("/api/v1/learn/goals/select").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("certificateId", certId, "subjectId", subject1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/learn/goals/select").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("certificateId", certId, "subjectId", subject1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/learn/goals").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void select_otherSubject_switchesStatus_historyKept() throws Exception {
        select(subject1);
        long goal1 = currentGoalId();
        select(subject2);

        MvcResult result = mockMvc.perform(get("/api/v1/learn/goals").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();
        JsonNode arr = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        for (JsonNode g : arr) {
            if (g.get("subjectId").asLong() == subject1) {
                assertEquals(0, g.get("status").asInt(), "旧目标应为历史状态");
                assertEquals(goal1, g.get("id").asLong());
            } else if (g.get("subjectId").asLong() == subject2) {
                assertEquals(1, g.get("status").asInt(), "新目标应为当前状态");
            }
        }
        mockMvc.perform(get("/api/v1/learn/goals/current").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.subjectId").value(subject2))
                .andExpect(jsonPath("$.data.status").value(1));
    }

    @Test
    void switch_keepsLearningData() throws Exception {
        QuestionPracticeRecord record = new QuestionPracticeRecord();
        record.setUserId(learnerId);
        record.setQuestionId(999001L);
        record.setPracticeMode(1);
        record.setSubmittedAnswer("A");
        record.setStandardAnswer("A");
        record.setCorrect(1);
        record.setAnswerTime(LocalDateTime.now());
        practiceRecordMapper.insert(record);

        select(subject1);
        select(subject2);

        assertEquals(1L, practiceRecordMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<QuestionPracticeRecord>()
                        .eq(QuestionPracticeRecord::getUserId, learnerId)),
                "切换考试目标不得删除历史学习数据（练习记录仍在）");
    }

    // ---------- helpers ----------

    private void select(Long subjectId) throws Exception {
        mockMvc.perform(post("/api/v1/learn/goals/select").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("certificateId", certId, "subjectId", subjectId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    private long currentGoalId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/learn/goals/current").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
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
