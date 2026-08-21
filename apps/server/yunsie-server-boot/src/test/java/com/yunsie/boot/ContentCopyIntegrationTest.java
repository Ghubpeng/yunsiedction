package com.yunsie.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 2.3B 内容复制集成测试：复制证书体系（科目/章节/知识点/课程结构/可选题库）。
 * 断言：新证书下体系与课程数量一致、内容树归属重映射、题目为草稿（审核铁律）、视频不复制。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContentCopyIntegrationTest {

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
                Map.of("parentId", 0L, "name", "分类", "code", "cat-copy-it")));
        certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "复制源证书", "code", "cert-copy-src")));
        versionId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        subjectId = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "科目", "code", "subj-copy-src")));
        chapterNodeId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", 0L, "nodeType", 1,
                        "name", "章节", "code", "ch-copy-src", "sort", 1, "enabled", 1, "source", 1)));
        kpNodeId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", chapterNodeId, "nodeType", 2,
                        "name", "知识点", "code", "kp-copy-src", "sort", 1, "enabled", 1, "source", 1)));
    }

    @Test
    void copyCertificate_fullSystem() throws Exception {
        // 源内容：1 门发布课程（章/节/知识点关联）+ 1 道已发布题目
        long courseId = dataId(postJson("/api/v1/course/courses", adminToken,
                Map.of("certificateId", certId, "teacherId", 2L, "title", "源课程", "description", "",
                        "subjectId", subjectId, "versionId", versionId, "chapterId", chapterNodeId)));
        long chId = dataId(postJson("/api/v1/course/chapters", adminToken,
                Map.of("courseId", courseId, "title", "第一章", "sort", 0)));
        long lessonId = dataId(postJson("/api/v1/course/lessons", adminToken,
                Map.of("chapterId", chId, "title", "小节", "durationSeconds", 600, "sort", 0)));
        mockMvc.perform(put("/api/v1/course/lessons/{id}/knowledge-nodes", lessonId).header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nodeIds", List.of(kpNodeId)))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/course/courses/{id}/publish", courseId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        long questionId = dataId(postJson("/api/v1/question/questions", adminToken,
                Map.of("certificateId", certId, "questionType", 1, "stem", "复制源题", "analysis", "解析",
                        "answer", "A", "difficulty", 2, "source", 2,
                        "options", List.of(Map.of("optionKey", "A", "content", "甲"),
                                Map.of("optionKey", "B", "content", "乙")),
                        "nodeIds", List.of(kpNodeId))));
        mockMvc.perform(post("/api/v1/question/questions/{id}/submit-review", questionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/question/questions/{id}/approve", questionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());

        // 复制（含题库）
        MvcResult copyResult = mockMvc.perform(post("/api/v1/content/certificates/{id}/copy", certId)
                        .header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "复制目标证书", "code", "cert-copy-dst", "copyQuestions", true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.subjectCount").value(1))
                .andExpect(jsonPath("$.data.nodeCount").value(2))
                .andExpect(jsonPath("$.data.courseCount").value(1))
                .andExpect(jsonPath("$.data.questionCount").value(1))
                .andReturn();
        Long targetCertId = objectMapper.readTree(copyResult.getResponse().getContentAsString())
                .get("data").get("targetCertificateId").asLong();

        // 目标证书：知识体系完整 + 课程内容树归属重映射
        mockMvc.perform(get("/api/v1/subject/subjects").param("certificateId", String.valueOf(targetCertId))
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/v1/course/courses").param("certificateId", String.valueOf(targetCertId))
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].status").value(1))
                .andExpect(jsonPath("$.data.list[0].subjectId").isNumber())
                .andExpect(jsonPath("$.data.list[0].chapterId").isNumber());
        // 目标课程章节结构复制（1 章 1 节，无视频）
        MvcResult targetCourse = mockMvc.perform(get("/api/v1/course/courses")
                        .param("certificateId", String.valueOf(targetCertId)).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andReturn();
        Long targetCourseId = objectMapper.readTree(targetCourse.getResponse().getContentAsString())
                .get("data").get("list").get(0).get("id").asLong();
        mockMvc.perform(get("/api/v1/course/courses/{id}/chapters", targetCourseId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].lessons.length()").value(1))
                .andExpect(jsonPath("$.data[0].lessons[0].videoKey").doesNotExist());
        // 目标题目为草稿（发布唯一通道=审核）
        mockMvc.perform(get("/api/v1/question/questions").param("certificateId", String.valueOf(targetCertId))
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].status").value(1));
    }

    @Test
    void copyCertificate_noQuestions_whenFlagFalse() throws Exception {
        long questionId = dataId(postJson("/api/v1/question/questions", adminToken,
                Map.of("certificateId", certId, "questionType", 1, "stem", "可选复制题", "analysis", "解析",
                        "answer", "A", "difficulty", 2, "source", 2,
                        "options", List.of(Map.of("optionKey", "A", "content", "甲"),
                                Map.of("optionKey", "B", "content", "乙")),
                        "nodeIds", List.of(kpNodeId))));
        mockMvc.perform(post("/api/v1/question/questions/{id}/submit-review", questionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/question/questions/{id}/approve", questionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());

        MvcResult copyResult = mockMvc.perform(post("/api/v1/content/certificates/{id}/copy", certId)
                        .header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "不复制题库", "code", "cert-copy-dst2"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.questionCount").value(0))
                .andReturn();
        Long targetCertId = objectMapper.readTree(copyResult.getResponse().getContentAsString())
                .get("data").get("targetCertificateId").asLong();
        mockMvc.perform(get("/api/v1/question/questions").param("certificateId", String.valueOf(targetCertId))
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
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
