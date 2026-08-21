package com.yunsie.boot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunsie.module.user.api.UserAuthApi;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公开课程浏览集成测试（Stage 2.1）：
 * 匿名可读公开课程列表/大纲（无进度），播放凭证与进度必须登录（个人数据不公开）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PublicAccessIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserAuthApi userAuthApi;

    private long courseId;
    private long lessonId;

    @BeforeEach
    void setup() throws Exception {
        String adminToken = login("admin", "Admin@123456");
        long adminId = userAuthApi.findByAccount("admin").userId();
        long categoryId = dataId(postJson("/api/v1/certificate/categories", adminToken,
                Map.of("parentId", 0L, "name", "公开分类", "code", "cat-public")));
        long certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "护士执业资格考试", "code", "cert-public")));
        courseId = dataId(postJson("/api/v1/course/courses", adminToken,
                Map.of("certificateId", certId, "teacherId", adminId, "title", "公开课程", "description", "公开课程简介")));
        long chapterId = dataId(postJson("/api/v1/course/chapters", adminToken,
                Map.of("courseId", courseId, "title", "公开章节", "sort", 0)));
        lessonId = dataId(postJson("/api/v1/course/lessons", adminToken,
                Map.of("chapterId", chapterId, "title", "公开小节", "durationSeconds", 600, "sort", 0)));
        mockMvc.perform(post("/api/v1/course/courses/{id}/publish", courseId).header(AUTH, "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void anonymous_canListPublicCourses() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/course/public/courses")
                        .param("pageNum", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("list");
        boolean found = false;
        for (JsonNode node : list) {
            if (node.get("title").asText().equals("公开课程")) {
                found = true;
            }
        }
        assertTrue(found, "匿名公开列表应包含已发布课程");
    }

    @Test
    void anonymous_canReadCourseOutline_withoutProgress() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/course/public/courses/{id}/outline", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.chapters[0].lessons[0].title").value("公开小节"))
                .andReturn();
        JsonNode lesson = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("chapters").get(0).get("lessons").get(0);
        assertEquals(0, lesson.get("positionSeconds").asInt(), "匿名大纲不得携带任何个人进度");
        assertEquals(0, lesson.get("finished").asInt());
    }

    @Test
    void anonymous_cannotPlayLesson_401() throws Exception {
        mockMvc.perform(get("/api/v1/course/lessons/{id}/play", lessonId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticated_treeStillIncludesProgress() throws Exception {
        // 登录用户走 tree（带进度）接口：契约不受匿名 outline 影响
        String learnerToken = login("admin", "Admin@123456");
        mockMvc.perform(get("/api/v1/course/public/courses/{id}/tree", courseId)
                        .header(AUTH, "Bearer " + learnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.chapters[0].lessons[0].positionSeconds").value(0));
    }

    // ---------- helpers ----------

    private long dataId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    private MvcResult postJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(post(path).header(AUTH, "Bearer " + token)
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

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
