package com.yunsie.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 2.3B 内容运营看板集成测试：资产统计（证书/课程/视频/题目）+
 * 运营提醒（待审核题目/空章节/无课程章节）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContentOpsDashboardIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private Long certId;
    private Long subjectId;
    private Long versionId;
    private Long chapterNodeId;
    private Long kpNodeId;

    @BeforeEach
    void setup() throws Exception {
        adminToken = login("admin", "Admin@123456");
        long categoryId = dataId(postJson("/api/v1/certificate/categories", adminToken,
                Map.of("parentId", 0L, "name", "分类", "code", "cat-ops-it")));
        // sort=-1：证书排序最前，孤儿章节扫描最先处理本证书（隔离 dev 库其他证书数据）
        certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "看板证书", "code", "cert-ops-it", "sort", -1)));
        versionId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        subjectId = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "科目", "code", "subj-ops-it")));
        // 无课程章节（未被任何课程归属）
        chapterNodeId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", 0L, "nodeType", 1,
                        "name", "无课程章节", "code", "ch-ops-it", "sort", 1, "enabled", 1, "source", 1)));
        kpNodeId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", chapterNodeId, "nodeType", 2,
                        "name", "知识点", "code", "kp-ops-it", "sort", 1, "enabled", 1, "source", 1)));
    }

    @Test
    void dashboard_assetsAndReminders() throws Exception {
        // 课程不绑定章节（chapterNodeId 保持「无课程章节」）；课程章下无小节 → 空章节
        long courseId = dataId(postJson("/api/v1/course/courses", adminToken,
                Map.of("certificateId", certId, "teacherId", 2L, "title", "看板课程", "description", "")));
        dataId(postJson("/api/v1/course/chapters", adminToken,
                Map.of("courseId", courseId, "title", "空章节", "sort", 0)));
        // 待审核题目
        long questionId = dataId(postJson("/api/v1/question/questions", adminToken,
                Map.of("certificateId", certId, "questionType", 1, "stem", "待审核看板题", "analysis", "解析",
                        "answer", "A", "difficulty", 2, "source", 2,
                        "options", java.util.List.of(Map.of("optionKey", "A", "content", "甲"),
                                Map.of("optionKey", "B", "content", "乙")),
                        "nodeIds", java.util.List.of(kpNodeId))));
        mockMvc.perform(post("/api/v1/question/questions/{id}/submit-review", questionId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/content/dashboard").header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.assets.certificates").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.assets.courses").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.assets.questions").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.assets.videos").isNumber())
                .andExpect(jsonPath("$.data.reminders.pendingReviewQuestions").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.reminders.emptyChapters").isArray())
                .andExpect(jsonPath("$.data.reminders.orphanChapters").isArray())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("无课程章节"),
                "看板应包含本证书的无课程章节提醒（证书 sort=-1 最先扫描）: " + body);
    }

    // ---------- helpers ----------

    private long dataId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    private MvcResult postJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(post(path).header(AUTH, bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk()).andReturn();
    }

    private String login(String account, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/user/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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
