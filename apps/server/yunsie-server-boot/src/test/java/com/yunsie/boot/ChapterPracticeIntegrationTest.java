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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 章节练习集成测试（Stage 2.1）：章节关联知识点 → 取该章节节点已发布题目（不含答案与解析）；
 * 未关联节点的章节返回空列表。判分仍走既有 POST /practice/submit（不改）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChapterPracticeIntegrationTest {

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
        userService.create(new UserCreateReq("it_chap_learner", null, "学员", "Learner@1234", 1, null));
        learnerToken = login("it_chap_learner", "Learner@1234");

        long categoryId = dataId(postJson("/api/v1/certificate/categories", adminToken,
                Map.of("parentId", 0L, "name", "分类", "code", "cat-chap")));
        certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "护士执业资格考试", "code", "cert-chap")));
        long versionId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        long subjectId = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "专业实务", "code", "subj-chap")));
        long chapterNodeId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", 0L,
                        "nodeType", 1, "name", "基础护理", "code", "ch-chap")));
        kpId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", chapterNodeId,
                        "nodeType", 2, "name", "无菌技术", "code", "kp-chap")));
    }

    @Test
    void chapterPractice_returnsNodeQuestions_withoutAnswer() throws Exception {
        long questionId = publishQuestion("章节练习题");
        long courseChapterId = createCourseChapterWithNodeLink(kpId);

        MvcResult result = mockMvc.perform(get("/api/v1/question/practice/chapter/{chapterId}", courseChapterId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(questionId))
                .andExpect(jsonPath("$.data[0].answer").doesNotExist())
                .andExpect(jsonPath("$.data[0].analysis").doesNotExist())
                .andReturn();
        // 练习安全视图：响应体不含答案/解析字段（杜绝提前泄露）
        assertFalse(result.getResponse().getContentAsString().contains("\"answer\""));
        assertFalse(result.getResponse().getContentAsString().contains("\"analysis\""));
    }

    @Test
    void chapterPractice_withoutNodeLink_empty() throws Exception {
        long courseId = createCourse("空关联课");
        long chapterId = createChapter(courseId, "空章节");
        createLesson(chapterId, "无关联小节", 600);

        mockMvc.perform(get("/api/v1/question/practice/chapter/{chapterId}", chapterId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ---------- helpers ----------

    /** 建课程+章节+小节并关联知识点，返回课程章节ID */
    private long createCourseChapterWithNodeLink(Long nodeId) throws Exception {
        long courseId = createCourse("章节练习课");
        long chapterId = createChapter(courseId, "章节");
        long lessonId = createLesson(chapterId, "小节", 600);
        mockMvc.perform(put("/api/v1/course/lessons/{id}/knowledge-nodes", lessonId)
                        .header(AUTH, bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nodeIds", List.of(nodeId)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        return chapterId;
    }

    private long createCourse(String title) throws Exception {
        return dataId(postJson("/api/v1/course/courses", adminToken,
                Map.of("certificateId", certId, "teacherId", 2L, "title", title, "description", "")));
    }

    private long createChapter(long courseId, String title) throws Exception {
        return dataId(postJson("/api/v1/course/chapters", adminToken,
                Map.of("courseId", courseId, "title", title, "sort", 0)));
    }

    private long createLesson(long chapterId, String title, int duration) throws Exception {
        return dataId(postJson("/api/v1/course/lessons", adminToken,
                Map.of("chapterId", chapterId, "title", title, "durationSeconds", duration, "sort", 0)));
    }

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
