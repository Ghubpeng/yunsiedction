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
 * Stage 2.4 周学习报告集成测试：
 * 学习时间（学习日历）/完成章节（窗口内 finished 课时）/练习数量（日聚合）/
 * 掌握变化（本周练习且当前掌握≥60，无历史快照口径）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WeeklyReportIntegrationTest {

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
    private Long courseId;
    private Long chapterId;
    private Long lessonId;

    @BeforeEach
    void setup() throws Exception {
        adminToken = login("admin", "Admin@123456");
        userService.create(new UserCreateReq("it_week_learner", null, "学员", "Learner@1234", 1, null));
        learnerToken = login("it_week_learner", "Learner@1234");

        long categoryId = dataId(postJson("/api/v1/certificate/categories", adminToken,
                Map.of("parentId", 0L, "name", "分类", "code", "cat-week-it")));
        certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "周报证书", "code", "cert-week-it")));
        long versionId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        long subjectId = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "科目", "code", "subj-week-it")));
        long chapterNodeId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", 0L, "nodeType", 1,
                        "name", "章节", "code", "ch-week-it", "sort", 1, "enabled", 1, "source", 1)));
        kpId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", chapterNodeId, "nodeType", 2,
                        "name", "知识点", "code", "kp-week-it", "sort", 1, "enabled", 1, "source", 1)));

        courseId = dataId(postJson("/api/v1/course/courses", adminToken,
                Map.of("certificateId", certId, "teacherId", 2L, "title", "周报课程", "description", "",
                        "subjectId", subjectId, "versionId", versionId, "chapterId", chapterNodeId)));
        chapterId = dataId(postJson("/api/v1/course/chapters", adminToken,
                Map.of("courseId", courseId, "title", "第一章", "sort", 0)));
        lessonId = dataId(postJson("/api/v1/course/lessons", adminToken,
                Map.of("chapterId", chapterId, "title", "小节", "durationSeconds", 30, "sort", 0)));
        mockMvc.perform(post("/api/v1/course/courses/{id}/publish", courseId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void weeklyReport_aggregates_real_data() throws Exception {
        // 练习一次（错 → 错题/掌握度数据）+ 完成一个课时
        long questionId = publishQuestion("周报题");
        mockMvc.perform(post("/api/v1/question/practice/submit").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("questionId", questionId, "answer", "B", "mode", 1, "nodeId", kpId,
                                "responseTimeMs", 2000))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.correct").value(false));
        mockMvc.perform(post("/api/v1/course/progress").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lessonId", lessonId, "positionSeconds", 30))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/learn/me/weekly-report").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.studySeconds").isNumber())
                .andExpect(jsonPath("$.data.finishedChapters").value(1))
                .andExpect(jsonPath("$.data.practiceCount").value(1))
                .andExpect(jsonPath("$.data.practicedNodes").value(1));
    }

    @Test
    void weeklyReport_noData_honestZero() throws Exception {
        mockMvc.perform(get("/api/v1/learn/me/weekly-report").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.finishedChapters").value(0))
                .andExpect(jsonPath("$.data.practiceCount").value(0));
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
