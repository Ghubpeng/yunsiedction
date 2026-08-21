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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 2.3A 内容管理生命周期集成测试：
 * 课程四层级归属校验（章节节点/启用/科目一致）、章节被课程引用禁删（probe 装配）、
 * 科目被课程引用禁删、知识点被题目引用禁删、题目发布后禁删/下架后可删（端到端 30319）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContentLifecycleIntegrationTest {

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
                Map.of("parentId", 0L, "name", "分类", "code", "cat-lc-it")));
        certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "生命周期证书", "code", "cert-lc-it")));
        versionId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        subjectId = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "科目", "code", "subj-lc-it")));
        chapterNodeId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", 0L, "nodeType", 1,
                        "name", "章节", "description", "章节描述", "code", "ch-lc-it", "sort", 1, "enabled", 1, "source", 1)));
        kpNodeId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", chapterNodeId, "nodeType", 2,
                        "name", "知识点", "code", "kp-lc-it", "sort", 1, "enabled", 1, "source", 1)));
    }

    @Test
    void course_createWithChapter_setsChapterId_andTreeReturnsChapterName() throws Exception {
        long courseId = dataId(postJson("/api/v1/course/courses", adminToken,
                Map.of("certificateId", certId, "teacherId", 2L, "title", "章节归属课", "description", "",
                        "subjectId", subjectId, "versionId", versionId, "chapterId", chapterNodeId)));

        MvcResult detail = mockMvc.perform(get("/api/v1/course/courses/{id}", courseId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.chapterId").value(chapterNodeId))
                .andExpect(jsonPath("$.data.chapterName").value("章节"))
                .andReturn();
        assertEquals(chapterNodeId, objectMapper.readTree(detail.getResponse().getContentAsString())
                .get("data").get("chapterId").asLong());
    }

    @Test
    void course_createWithKnowledgePoint_30209() throws Exception {
        mockMvc.perform(post("/api/v1/course/courses").header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("certificateId", certId, "teacherId", 2L, "title", "非法章节课",
                                "description", "", "subjectId", subjectId, "versionId", versionId,
                                "chapterId", kpNodeId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30209));
    }

    @Test
    void course_createWithDisabledChapter_30209() throws Exception {
        long disabledChapter = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", 0L, "nodeType", 1,
                        "name", "停用章节", "code", "ch-lc-it-off", "sort", 2, "enabled", 0, "source", 1)));
        mockMvc.perform(post("/api/v1/course/courses").header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("certificateId", certId, "teacherId", 2L, "title", "停用章节课",
                                "description", "", "subjectId", subjectId, "versionId", versionId,
                                "chapterId", disabledChapter))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30209));
    }

    @Test
    void chapterDelete_withCourse_30217() throws Exception {
        // 无子节点的章节被课程归属 → 30217（有子节点时先命中 30214，语义不同）
        long guardChapter = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", 0L, "nodeType", 1,
                        "name", "守卫章节", "code", "ch-lc-it-guard", "sort", 9, "enabled", 1, "source", 1)));
        dataId(postJson("/api/v1/course/courses", adminToken,
                Map.of("certificateId", certId, "teacherId", 2L, "title", "章节保护课", "description", "",
                        "subjectId", subjectId, "versionId", versionId, "chapterId", guardChapter)));
        mockMvc.perform(delete("/api/v1/subject/nodes/{id}", guardChapter).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30217));
    }

    @Test
    void subjectDelete_withCourse_30215() throws Exception {
        // 无节点的科目被课程归属（课程可空章节）→ 30215（有节点时先命中 30213，语义不同）
        long emptySubject = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "空科目", "code", "subj-lc-it-empty")));
        dataId(postJson("/api/v1/course/courses", adminToken,
                Map.of("certificateId", certId, "teacherId", 2L, "title", "科目保护课", "description", "",
                        "subjectId", emptySubject, "versionId", versionId)));
        mockMvc.perform(delete("/api/v1/subject/subjects/{id}", emptySubject).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30215));
    }

    @Test
    void nodeDelete_withQuestion_30218() throws Exception {
        dataId(postJson("/api/v1/question/questions", adminToken,
                Map.of("certificateId", certId, "questionType", 1, "stem", "节点保护题", "analysis", "解析",
                        "answer", "A", "difficulty", 2, "source", 2,
                        "options", List.of(Map.of("optionKey", "A", "content", "甲"),
                                Map.of("optionKey", "B", "content", "乙")),
                        "nodeIds", List.of(kpNodeId))));
        mockMvc.perform(delete("/api/v1/subject/nodes/{id}", kpNodeId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30218));
    }

    @Test
    void questionLifecycle_publishedDeleteRecycles_restoreBackToDraft() throws Exception {
        long questionId = dataId(postJson("/api/v1/question/questions", adminToken,
                Map.of("certificateId", certId, "questionType", 1, "stem", "生命周期题", "analysis", "解析",
                        "answer", "A", "difficulty", 2, "source", 2,
                        "options", List.of(Map.of("optionKey", "A", "content", "甲"),
                                Map.of("optionKey", "B", "content", "乙")),
                        "nodeIds", List.of(kpNodeId))));
        mockMvc.perform(post("/api/v1/question/questions/{id}/submit-review", questionId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/question/questions/{id}/approve", questionId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        // 已发布删除 → 回收站（非物理删除，历史快照不受影响）
        mockMvc.perform(delete("/api/v1/question/questions/{id}", questionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        // 恢复 → 草稿；随后可逻辑删除（下架语义）
        mockMvc.perform(post("/api/v1/question/questions/{id}/restore", questionId)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/v1/question/questions/{id}", questionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
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
