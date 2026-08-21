package com.yunsie.boot;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 2.2：证书级考试目标（用户只选证书，门槛最低化）。
 * 覆盖：只选证书自动生成目标（科目隐藏）/ 课程按目标证书过滤 /
 * 科目数据仍在 subject 域（隐藏复杂度而非删除）/ 切换目标历史保留 / 科目级能力向后兼容。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GoalCertificateLevelIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserService userService;

    private String learnerToken;
    private long certId;
    private long certBId;
    private long subjectId;
    private long courseId;

    @BeforeEach
    void setup() throws Exception {
        userService.create(new UserCreateReq("it_goal_cert_learner", null, "证书级学员", "Learner@1234", 1, null));
        String adminToken = login("admin", "Admin@123456");
        long adminId = userService.create(new UserCreateReq("it_goal_cert_teacher", null, "教师", "Teacher@1234", 2, null));
        learnerToken = login("it_goal_cert_learner", "Learner@1234");

        long categoryId = dataId(postJson("/api/v1/certificate/categories", adminToken,
                Map.of("parentId", 0L, "name", "分类", "code", "cat-goal-cert")));
        certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "证书级目标考试", "code", "cert-goal-cert")));
        long versionId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        subjectId = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "科目甲", "code", "subj-goal-cert")));

        long categoryBId = dataId(postJson("/api/v1/certificate/categories", adminToken,
                Map.of("parentId", 0L, "name", "分类B", "code", "cat-goal-cert-b")));
        certBId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryBId, "name", "证书B", "code", "cert-goal-cert-b")));
        long versionBId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certBId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionBId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());

        courseId = dataId(postJson("/api/v1/course/courses", adminToken,
                Map.of("certificateId", certId, "subjectId", subjectId, "teacherId", adminId,
                        "title", "证书级课程", "description", "desc")));
        long chapterId = dataId(postJson("/api/v1/course/chapters", adminToken,
                Map.of("courseId", courseId, "title", "章节", "sort", 0)));
        postJson("/api/v1/course/lessons", adminToken,
                Map.of("chapterId", chapterId, "title", "小节", "durationSeconds", 30, "sort", 0));
        // 发布：公开课程列表仅含已发布课程
        mockMvc.perform(post("/api/v1/course/courses/{id}/publish", courseId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void selectCertOnly_createsCertLevelGoal_subjectHidden() throws Exception {
        mockMvc.perform(post("/api/v1/learn/goals/select")
                        .header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("certificateId", certId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.certificateName").value("证书级目标考试"))
                .andExpect(jsonPath("$.data.subjectId").value(nullValue()))
                .andExpect(jsonPath("$.data.subjectName").value(nullValue()));
        mockMvc.perform(get("/api/v1/learn/goals/current").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.certificateId").value(certId));
    }

    @Test
    void subjectDataRemains_coursesFilterByCertGoal() throws Exception {
        mockMvc.perform(post("/api/v1/learn/goals/select")
                        .header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("certificateId", certId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        // 科目数据仍在 subject 域（隐藏复杂度，不删除）
        mockMvc.perform(get("/api/v1/subject/public/subjects").param("certificateId", String.valueOf(certId))
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1));
        // 课程按目标证书自动组织（证书级目标覆盖该证书全部科目课程）
        MvcResult list = mockMvc.perform(get("/api/v1/course/public/courses")
                        .param("certificateId", String.valueOf(certId))
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode items = objectMapper.readTree(list.getResponse().getContentAsString()).get("data").get("list");
        boolean found = false;
        for (JsonNode n : items) {
            if (n.get("id").asLong() == courseId) {
                found = true;
            }
        }
        assertTrue(found, "证书级目标应按证书组织课程");
    }

    @Test
    void switchCertLevelGoal_historyPreserved() throws Exception {
        mockMvc.perform(post("/api/v1/learn/goals/select").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("certificateId", certId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/learn/goals/select").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("certificateId", certBId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.certificateName").value("证书B"));
        mockMvc.perform(get("/api/v1/learn/goals").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].status").value(1))
                .andExpect(jsonPath("$.data[1].status").value(0));
    }

    @Test
    void legacySubjectLevelSelect_stillWorks() throws Exception {
        mockMvc.perform(post("/api/v1/learn/goals/select").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("certificateId", certId, "subjectId", subjectId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.subjectId").value(subjectId))
                .andExpect(jsonPath("$.data.subjectName").value("科目甲"));
    }

    // ---------- helpers ----------

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
