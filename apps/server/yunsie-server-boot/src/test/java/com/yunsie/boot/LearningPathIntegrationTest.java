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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 2.4 学习路径集成测试：
 * 章节四态（未开始→学习中→已完成→掌握，由视频完成与章节练习完成共同决定）+
 * 总完成度/学习阶段/下一学习任务 + 证书→科目→章节→课时 结构。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LearningPathIntegrationTest {

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
    private Long chapterNodeId;
    private Long kpId;
    private Long courseId;
    private Long chapterId;
    private Long lessonId;

    @BeforeEach
    void setup() throws Exception {
        adminToken = login("admin", "Admin@123456");
        userService.create(new UserCreateReq("it_path_learner", null, "学员", "Learner@1234", 1, null));
        learnerToken = login("it_path_learner", "Learner@1234");

        long categoryId = dataId(postJson("/api/v1/certificate/categories", adminToken,
                Map.of("parentId", 0L, "name", "分类", "code", "cat-path-it")));
        certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "路径证书", "code", "cert-path-it")));
        long versionId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        subjectId = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "科目", "code", "subj-path-it")));
        chapterNodeId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", 0L, "nodeType", 1,
                        "name", "章节", "code", "ch-path-it", "sort", 1, "enabled", 1, "source", 1)));
        kpId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", chapterNodeId, "nodeType", 2,
                        "name", "知识点", "code", "kp-path-it", "sort", 1, "enabled", 1, "source", 1)));

        // 已发布课程：1 章 1 节（30 秒）
        courseId = dataId(postJson("/api/v1/course/courses", adminToken,
                Map.of("certificateId", certId, "teacherId", 2L, "title", "路径课程", "description", "",
                        "subjectId", subjectId, "versionId", versionId, "chapterId", chapterNodeId)));
        chapterId = dataId(postJson("/api/v1/course/chapters", adminToken,
                Map.of("courseId", courseId, "title", "第一章", "sort", 0)));
        lessonId = dataId(postJson("/api/v1/course/lessons", adminToken,
                Map.of("chapterId", chapterId, "title", "小节", "durationSeconds", 30, "sort", 0)));
        mockMvc.perform(put("/api/v1/course/lessons/{id}/knowledge-nodes", lessonId).header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nodeIds", List.of(kpId)))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/course/courses/{id}/publish", courseId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void learningPath_states_transition() throws Exception {
        // 1) 未开始：章节 todo，总完成度 0，下一任务=课时
        mockMvc.perform(get("/api/v1/course/me/learning-path").param("certificateId", String.valueOf(certId))
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalChapters").value(1))
                .andExpect(jsonPath("$.data.finishedChapters").value(0))
                .andExpect(jsonPath("$.data.percent").value(0))
                .andExpect(jsonPath("$.data.stage").value("起步"))
                .andExpect(jsonPath("$.data.nextTask.type").value("lesson"))
                .andExpect(jsonPath("$.data.subjects[0].subjectName").value("科目"))
                .andExpect(jsonPath("$.data.subjects[0].courses[0].chapters[0].state").value("todo"));

        // 2) 学习中：报一次进度
        mockMvc.perform(post("/api/v1/course/progress").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lessonId", lessonId, "positionSeconds", 10))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/course/me/learning-path").param("certificateId", String.valueOf(certId))
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(jsonPath("$.data.subjects[0].courses[0].chapters[0].state").value("learning"));

        // 3) 已完成：完成视频（duration*0.95=29 → finished）
        mockMvc.perform(post("/api/v1/course/progress").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lessonId", lessonId, "positionSeconds", 30))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/course/me/learning-path").param("certificateId", String.valueOf(certId))
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(jsonPath("$.data.finishedChapters").value(1))
                .andExpect(jsonPath("$.data.masteredChapters").value(0))
                .andExpect(jsonPath("$.data.percent").value(100))
                .andExpect(jsonPath("$.data.stage").value("冲刺"))
                .andExpect(jsonPath("$.data.nextTask.type").value("practice"))
                .andExpect(jsonPath("$.data.subjects[0].courses[0].chapters[0].state").value("done"));

        // 4) 掌握：上报章节练习完成（视频完成 + 练习完成）
        mockMvc.perform(post("/api/v1/course/chapters/{id}/practice-result", chapterId)
                        .header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("correctCount", 4, "totalCount", 5))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.score").value(80));
        mockMvc.perform(get("/api/v1/course/me/learning-path").param("certificateId", String.valueOf(certId))
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(jsonPath("$.data.masteredChapters").value(1))
                .andExpect(jsonPath("$.data.subjects[0].courses[0].chapters[0].state").value("mastered"))
                .andExpect(jsonPath("$.data.subjects[0].courses[0].chapters[0].practiceScore").value(80));

        // 课程树同样带练习状态（掌握判定依据：finished + practiceFinished）
        mockMvc.perform(get("/api/v1/course/public/courses/{id}/tree", courseId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.chapters[0].finished").value(1))
                .andExpect(jsonPath("$.data.chapters[0].practiceFinished").value(1))
                .andExpect(jsonPath("$.data.chapters[0].practiceScore").value(80));
    }

    @Test
    void chapterPracticeResult_invalid_30516() throws Exception {
        mockMvc.perform(post("/api/v1/course/chapters/{id}/practice-result", chapterId)
                        .header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("correctCount", 6, "totalCount", 5))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30516));
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
