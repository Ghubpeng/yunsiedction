package com.yunsie.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunsie.module.user.dto.UserCreateReq;
import com.yunsie.module.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
 * Stage 2.3B AI 学习助手规则增强集成测试（无真实 LLM）：
 * 基于真实错题/掌握度生成 薄弱知识点 → 推荐课程 → 推荐练习 → 学习下一步；数据不足诚实返回。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NextStepIntegrationTest {

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
        userService.create(new UserCreateReq("it_next_learner", null, "学员", "Learner@1234", 1, null));
        learnerToken = login("it_next_learner", "Learner@1234");

        long categoryId = dataId(postJson("/api/v1/certificate/categories", adminToken,
                Map.of("parentId", 0L, "name", "分类", "code", "cat-next-it")));
        certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "下一步证书", "code", "cert-next-it")));
        long versionId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        long subjectId = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "科目", "code", "subj-next-it")));
        long chapterNodeId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", 0L, "nodeType", 1,
                        "name", "章节", "code", "ch-next-it", "sort", 1, "enabled", 1, "source", 1)));
        kpId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", chapterNodeId, "nodeType", 2,
                        "name", "无菌技术", "code", "kp-next-it", "sort", 1, "enabled", 1, "source", 1)));
    }

    @Test
    void nextStep_withWrongAnswer_weakKnowledge_course_practice() throws Exception {
        long questionId = publishQuestion("下一步规则题");
        // 覆盖薄弱知识点的已发布课程
        long courseId = dataId(postJson("/api/v1/course/courses", adminToken,
                Map.of("certificateId", certId, "teacherId", 2L, "title", "无菌技术精讲", "description", "")));
        long chId = dataId(postJson("/api/v1/course/chapters", adminToken,
                Map.of("courseId", courseId, "title", "第一章", "sort", 0)));
        long lessonId = dataId(postJson("/api/v1/course/lessons", adminToken,
                Map.of("chapterId", chId, "title", "小节", "durationSeconds", 600, "sort", 0)));
        mockMvc.perform(put("/api/v1/course/lessons/{id}/knowledge-nodes", lessonId).header(AUTH, bearer(adminToken))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nodeIds", List.of(kpId)))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/course/courses/{id}/publish", courseId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        // 学员答错 → 错题 + 掌握度
        mockMvc.perform(post("/api/v1/question/practice/submit").header(AUTH, bearer(learnerToken))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(Map.of("questionId", questionId, "answer", "B", "mode", 1, "nodeId", kpId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.correct").value(false));

        mockMvc.perform(get("/api/v1/learn/me/next-step").param("certificateId", String.valueOf(certId))
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.source").value("rule-based"))
                .andExpect(jsonPath("$.data.weakKnowledge[0].nodeId").value(kpId))
                .andExpect(jsonPath("$.data.recommendedCourses[0].courseId").value(courseId))
                .andExpect(jsonPath("$.data.recommendedPractice.length()").value(1))
                .andExpect(jsonPath("$.data.nextStep").value(containsString("无菌技术")));
    }

    @Test
    void nextStep_noData_honestEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/learn/me/next-step").param("certificateId", String.valueOf(certId))
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.source").value("rule-based"))
                .andExpect(jsonPath("$.data.weakKnowledge.length()").value(0))
                .andExpect(jsonPath("$.data.recommendedCourses.length()").value(0))
                .andExpect(jsonPath("$.data.recommendedPractice.length()").value(0))
                .andExpect(jsonPath("$.data.nextStep").value(containsString("暂无")));
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
