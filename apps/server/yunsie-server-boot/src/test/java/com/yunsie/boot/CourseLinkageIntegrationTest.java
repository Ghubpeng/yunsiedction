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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 课程内容树归属集成测试（Stage 2.1）：创建带 subjectId/versionId 的课程 →
 * 管理列表/公开列表可按证书+科目过滤；树返回 subjectId/versionId；全小节完成 → 章节 finished=1。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CourseLinkageIntegrationTest {

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
    private Long subjectId;
    private Long versionId;

    @BeforeEach
    void setup() throws Exception {
        adminToken = login("admin", "Admin@123456");
        userService.create(new UserCreateReq("it_link_learner", null, "学员", "Learner@1234", 1, null));
        learnerToken = login("it_link_learner", "Learner@1234");

        long categoryId = dataId(postJson("/api/v1/certificate/categories", adminToken,
                Map.of("parentId", 0L, "name", "分类", "code", "cat-link")));
        certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "护士执业资格考试", "code", "cert-link")));
        versionId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        subjectId = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "专业实务", "code", "subj-link")));
    }

    @Test
    void courseLinkage_filterTreeAndChapterFinish() throws Exception {
        // 创建带 subjectId/versionId 的课程并发布（章节+小节）
        long courseId = dataId(postJson("/api/v1/course/courses", adminToken,
                Map.of("certificateId", certId, "teacherId", 2L, "title", "归属课", "description", "",
                        "subjectId", subjectId, "versionId", versionId)));
        long chapterId = dataId(postJson("/api/v1/course/chapters", adminToken,
                Map.of("courseId", courseId, "title", "第一章", "sort", 0)));
        long lessonId = dataId(postJson("/api/v1/course/lessons", adminToken,
                Map.of("chapterId", chapterId, "title", "小节", "durationSeconds", 600, "sort", 0)));
        mockMvc.perform(post("/api/v1/course/courses/{id}/publish", courseId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        // 管理列表按证书+科目过滤（含 subjectId/versionId 回显）
        MvcResult adminResult = mockMvc.perform(get("/api/v1/course/courses")
                        .param("certificateId", String.valueOf(certId)).param("subjectId", String.valueOf(subjectId))
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andReturn();
        JsonNode adminCourse = objectMapper.readTree(adminResult.getResponse().getContentAsString())
                .get("data").get("list").get(0);
        assertEquals(subjectId, adminCourse.get("subjectId").asLong());
        assertEquals(versionId, adminCourse.get("versionId").asLong());

        // 公开列表按证书+科目过滤
        mockMvc.perform(get("/api/v1/course/public/courses")
                        .param("certificateId", String.valueOf(certId)).param("subjectId", String.valueOf(subjectId))
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1));

        // 树返回 subjectId/versionId
        mockMvc.perform(get("/api/v1/course/public/courses/{id}/tree", courseId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.subjectId").value(subjectId))
                .andExpect(jsonPath("$.data.versionId").value(versionId))
                .andExpect(jsonPath("$.data.chapters[0].finished").value(0));

        // 全小节完成（600 * 0.95 = 570）→ 章节 finished=1
        mockMvc.perform(post("/api/v1/course/progress").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lessonId", lessonId, "positionSeconds", 570))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/course/public/courses/{id}/tree", courseId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.chapters[0].finished").value(1));
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
