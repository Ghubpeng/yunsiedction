package com.yunsie.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 针对性练习推荐集成测试（Stage 2.1）：基于真实数据（错题节点优先）；
 * 无任何数据 → 空列表诚实返回（禁止用证书维度补齐）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecommendationIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserService userService;

    private String adminToken;
    private String learnerToken;
    private Long certId;
    private Long kpId;

    @BeforeEach
    void setup() throws Exception {
        adminToken = login("admin", "Admin@123456");
        userService.create(new UserCreateReq("it_rec_learner", null, "学员", "Learner@1234", 1, null));
        learnerToken = login("it_rec_learner", "Learner@1234");

        long categoryId = dataId(postJson("/api/v1/certificate/categories", adminToken,
                Map.of("parentId", 0L, "name", "分类", "code", "cat-rec")));
        certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "护士执业资格考试", "code", "cert-rec")));
        long versionId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        long subjectId = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "专业实务", "code", "subj-rec")));
        long chapterNodeId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", 0L,
                        "nodeType", 1, "name", "基础护理", "code", "ch-rec")));
        kpId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", chapterNodeId,
                        "nodeType", 2, "name", "无菌技术", "code", "kp-rec")));
    }

    @Test
    void recommendation_afterWrongAnswer_mentionsMistake_andReturnsQuestion() throws Exception {
        long questionId = publishQuestion("推荐题");
        // 学员答错（真实 API 判分 → 生成错题）
        mockMvc.perform(post("/api/v1/question/practice/submit").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("questionId", questionId, "answer", "B", "mode", 1, "nodeId", kpId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.correct").value(false));

        mockMvc.perform(get("/api/v1/learn/me/practice-recommendation")
                        .param("certificateId", String.valueOf(certId)).param("limit", "10")
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reason").value(containsString("错题")))
                .andExpect(jsonPath("$.data.nodes[0].id").value(kpId))
                .andExpect(jsonPath("$.data.questions.length()").value(1))
                .andExpect(jsonPath("$.data.questions[0].id").value(questionId))
                .andExpect(jsonPath("$.data.questions[0].answer").doesNotExist());
    }

    @Test
    void recommendation_noData_emptyHonestly() throws Exception {
        mockMvc.perform(get("/api/v1/learn/me/practice-recommendation")
                        .param("certificateId", String.valueOf(certId)).param("limit", "10")
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.questions.length()").value(0))
                .andExpect(jsonPath("$.data.reason").value(containsString("暂无")));
    }

    // ---------- helpers ----------

    private long publishQuestion(String stem) throws Exception {
        long id = dataId(postJson("/api/v1/question/questions", adminToken,
                Map.of("certificateId", certId, "questionType", 1, "stem", stem, "analysis", "解析",
                        "answer", "A", "difficulty", 2, "source", 2,
                        "options", List.of(Map.of("optionKey", "A", "content", "甲"), Map.of("optionKey", "B", "content", "乙")),
                        "nodeIds", List.of(kpId))));
        mockMvc.perform(post("/api/v1/question/questions/{id}/submit-review", id).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/question/questions/{id}/approve", id).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
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
