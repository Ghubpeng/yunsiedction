package com.yunsie.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunsie.module.course.api.CourseQueryApi;
import com.yunsie.module.course.entity.Course;
import com.yunsie.module.course.entity.CourseLesson;
import com.yunsie.module.course.mapper.CourseLessonMapper;
import com.yunsie.module.course.mapper.CourseMapper;
import com.yunsie.module.course.service.MinioStorageService;
import com.yunsie.module.sys.api.SysUserRoleApi;
import com.yunsie.module.sys.dto.RoleCreateReq;
import com.yunsie.module.sys.entity.SysPermission;
import com.yunsie.module.sys.mapper.SysPermissionMapper;
import com.yunsie.module.sys.service.SysRoleService;
import com.yunsie.module.user.dto.UserCreateReq;
import com.yunsie.module.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * course 域集成测试（真实 MySQL + 真实 MinIO 容器，事务回滚不留库数据）。
 * 覆盖：CRUD / 发布前置 / 章节小节 / MinIO 上传与对象存在 / 播放凭证 /
 * 公开树与禁用隐藏 / 进度与续播 / 教师数据范围 / 权限 403 / 契约 / 逻辑删除。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CourseIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private SysRoleService roleService;
    @Autowired
    private SysPermissionMapper permissionMapper;
    @Autowired
    private SysUserRoleApi sysUserRoleApi;
    @Autowired
    private CourseMapper courseMapper;
    @Autowired
    private CourseLessonMapper lessonMapper;
    @Autowired
    private MinioStorageService minioStorageService;
    @Autowired
    private CourseQueryApi courseQueryApi;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String learnerToken;
    private Long learnerId;
    private String teacherToken;
    private Long teacherId;
    private String teacherBToken;
    private Long teacherBId;
    private Long certId;
    private Long kpId;

    @BeforeEach
    void setup() throws Exception {
        adminToken = login("admin", "Admin@123456");
        learnerId = userService.create(new UserCreateReq("it_course_learner", null, "学员", "Learner@1234", 1, null));
        learnerToken = login("it_course_learner", "Learner@1234");
        teacherId = userService.create(new UserCreateReq("it_course_teacher", null, "教师", "Teacher@1234", 2, null));
        teacherToken = login("it_course_teacher", "Teacher@1234");
        teacherBId = userService.create(new UserCreateReq("it_course_teacher_b", null, "教师B", "TeacherB@1234", 2, null));
        teacherBToken = login("it_course_teacher_b", "TeacherB@1234");
        // 教师授予课程管理权限点（create/update/delete/read/publish/upload）
        Long roleId = roleService.create(new RoleCreateReq("it_course_teacher_role", "课程教师", 2, 1, "", 0));
        List<SysPermission> perms = permissionMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysPermission>()
                .likeRight(SysPermission::getPermissionCode, "course:"));
        roleService.assignPermissions(roleId, perms.stream().map(SysPermission::getId).toList());
        sysUserRoleApi.assignRoles(teacherId, List.of(roleId));
        sysUserRoleApi.assignRoles(teacherBId, List.of(roleId));

        long categoryId = dataId(postJson("/api/v1/certificate/categories", adminToken,
                Map.of("parentId", 0L, "name", "分类", "code", "cat-course")));
        certId = dataId(postJson("/api/v1/certificate/certificates", adminToken,
                Map.of("categoryId", categoryId, "name", "护士执业资格考试", "code", "cert-course")));
        long versionId = dataId(postJson("/api/v1/subject/versions", adminToken,
                Map.of("certificateId", certId, "versionNo", "2026", "name", "2026版")));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        long subjectId = dataId(postJson("/api/v1/subject/subjects", adminToken,
                Map.of("certificateId", certId, "name", "专业实务", "code", "subj-course")));
        long chapterId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", 0L,
                        "nodeType", 1, "name", "基础护理", "code", "ch-course")));
        kpId = dataId(postJson("/api/v1/subject/nodes", adminToken,
                Map.of("versionId", versionId, "subjectId", subjectId, "parentId", chapterId,
                        "nodeType", 2, "name", "无菌技术", "code", "kp-course")));
    }

    // ---------- 课程 CRUD / 状态机 ----------

    @Test
    void courseCrud_publishRequiresContent() throws Exception {
        long courseId = createCourse(adminToken, adminId(), "课程A");
        // 无内容发布 → 30505
        mockMvc.perform(post("/api/v1/course/courses/{id}/publish", courseId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30505));
        // 加章节+小节后发布
        long chapterId = createChapter(adminToken, courseId, "第一章");
        long lessonId = createLesson(adminToken, chapterId, "小节1", 600);
        mockMvc.perform(post("/api/v1/course/courses/{id}/publish", courseId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        // 已发布禁编辑/删除
        mockMvc.perform(put("/api/v1/course/courses/{id}", courseId).header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "改名", "description", ""))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30503));
        mockMvc.perform(delete("/api/v1/course/courses/{id}", courseId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30504));
        // 已发布仅允许启用/禁用小节（30503 以外）
        mockMvc.perform(put("/api/v1/course/lessons/{id}/status", lessonId).header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", 0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        // 下架后可编辑
        mockMvc.perform(post("/api/v1/course/courses/{id}/unpublish", courseId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(put("/api/v1/course/courses/{id}", courseId).header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "改名成功", "description", ""))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    // ---------- MinIO 视频上传与播放 ----------

    @Test
    void videoUpload_andPlay() throws Exception {
        // 内容编辑（上传/关联）必须在发布前完成
        long courseId = createCourse(adminToken, adminId(), "视频课");
        long chapterId = createChapter(adminToken, courseId, "章节");
        long lessonId = createLesson(adminToken, chapterId, "小节", 600);
        byte[] video = "fake-mp4-bytes-for-e2e".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "lesson1.mp4", "video/mp4", video);
        mockMvc.perform(multipart("/api/v1/course/lessons/{id}/video", lessonId).file(file)
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/course/courses/{id}/publish", courseId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        CourseLesson lesson = lessonMapper.selectById(lessonId);
        assertNotNull(lesson.getVideoKey());
        assertTrue(minioStorageService.exists(lesson.getVideoKey()), "MinIO 中必须存在已上传对象");

        // 播放凭证
        MvcResult play = mockMvc.perform(get("/api/v1/course/lessons/{id}/play", lessonId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.url").isNotEmpty())
                .andReturn();
        String url = objectMapper.readTree(play.getResponse().getContentAsString()).get("data").get("url").asText();
        // 预签名 URL 可直接访问（真实 MinIO）
        int httpStatus = java.net.http.HttpClient.newHttpClient().send(
                        java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).GET().build(),
                        java.net.http.HttpResponse.BodyHandlers.discarding())
                .statusCode();
        assertEquals(200, httpStatus, "预签名 URL 应可访问 MinIO 对象");
    }

    @Test
    void play_unpublishedCourse_rejected_30510() throws Exception {
        long courseId = createCourse(adminToken, adminId(), "未发布课");
        long chapterId = createChapter(adminToken, courseId, "章");
        long lessonId = createLesson(adminToken, chapterId, "节", 60);
        mockMvc.perform(get("/api/v1/course/lessons/{id}/play", lessonId).header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30510));
    }

    // ---------- 公开树 / 进度 / 续播 ----------

    @Test
    void publicTree_progressAndDisabledHidden() throws Exception {
        long courseId = createCourse(adminToken, adminId(), "学习课");
        long chapter1 = createChapter(adminToken, courseId, "启用章");
        long lesson1 = createLesson(adminToken, chapter1, "启用节", 600);
        long chapter2 = createChapter(adminToken, courseId, "禁用章");
        createLesson(adminToken, chapter2, "禁用节", 60);
        mockMvc.perform(put("/api/v1/course/chapters/{id}/status", chapter2).header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", 0))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/course/courses/{id}/publish", courseId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        // 公开树：禁用章隐藏
        MvcResult tree = mockMvc.perform(get("/api/v1/course/public/courses/{id}/tree", courseId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.chapters.length()").value(1))
                .andExpect(jsonPath("$.data.chapters[0].lessons[0].positionSeconds").value(0))
                .andReturn();
        assertTrue(!tree.getResponse().getContentAsString().contains("禁用章"));

        // 进度上报 → 树回显（续播点）
        mockMvc.perform(post("/api/v1/course/progress").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lessonId", lesson1, "positionSeconds", 300))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/course/public/courses/{id}/tree", courseId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chapters[0].lessons[0].positionSeconds").value(300));

        // 95% 完成：600*0.95=570 → 570 完成
        mockMvc.perform(post("/api/v1/course/progress").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lessonId", lesson1, "positionSeconds", 570))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/course/public/courses/{id}/tree", courseId)
                        .header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chapters[0].lessons[0].finished").value(1));

        // my-courses 续播入口
        mockMvc.perform(get("/api/v1/course/my-courses").header(AUTH, bearer(learnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].courseId").value(courseId))
                .andExpect(jsonPath("$.data[0].lastPositionSeconds").value(570));
    }

    // ---------- 权限 / 数据范围 ----------

    @Test
    void teacherA_cannotManageTeacherBCourse_30506() throws Exception {
        // 教师不能代他人建课（30506 已在创建路径验证）；管理员代建 B 的课，教师 A 尝试编辑 → 30506
        long courseId = createCourse(adminToken, teacherBId, "教师B的课");
        mockMvc.perform(put("/api/v1/course/courses/{id}", courseId).header(AUTH, bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "越权改名", "description", ""))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30506));
        // 教师管理自己的课 OK
        long ownCourse = createCourse(teacherToken, teacherId, "教师A自己的课");
        mockMvc.perform(put("/api/v1/course/courses/{id}", ownCourse).header(AUTH, bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "改名", "description", ""))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void learner_cannotManageCourses_403() throws Exception {
        mockMvc.perform(post("/api/v1/course/courses").header(AUTH, bearer(learnerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("certificateId", certId, "teacherId", teacherId,
                                "title", "越权", "description", ""))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    void teacherDataScope_listOnlyOwnCourses() throws Exception {
        createCourse(teacherToken, teacherId, "A的课");
        createCourse(adminToken, teacherBId, "B的课");
        mockMvc.perform(get("/api/v1/course/courses").header(AUTH, bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }

    // ---------- 知识点关联 / 契约 / 逻辑删除 ----------

    @Test
    void lessonKnowledgeNode_association() throws Exception {
        // 关联必须在发布前完成
        long courseId = createCourse(adminToken, adminId(), "关联课");
        long chapterId = createChapter(adminToken, courseId, "章节");
        long lessonId = createLesson(adminToken, chapterId, "小节", 600);
        mockMvc.perform(put("/api/v1/course/lessons/{id}/knowledge-nodes", lessonId)
                        .header(AUTH, bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nodeIds", List.of(kpId)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        // 非法节点（不存在）
        mockMvc.perform(put("/api/v1/course/lessons/{id}/knowledge-nodes", lessonId)
                        .header(AUTH, bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nodeIds", List.of(999999L)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30513));
    }

    @Test
    void courseQueryApi_contract() throws Exception {
        long courseId = publishCourse(adminToken, "契约课", 1);
        assertNotNull(courseQueryApi.findPublishedCourse(courseId));
        assertNotNull(courseQueryApi.findCourse(courseId));
        Long lessonId = lessonIdOf(courseId);
        assertNotNull(courseQueryApi.findLesson(lessonId));
        assertTrue(courseQueryApi.existsPublishedLesson(lessonId));
    }

    @Test
    void logicDelete_courseEffective() throws Exception {
        long courseId = createCourse(adminToken, adminId(), "删除课");
        mockMvc.perform(delete("/api/v1/course/courses/{id}", courseId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertNull(courseMapper.selectById(courseId));
        Long raw = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_course WHERE id=? AND deleted=1", Long.class, courseId);
        assertEquals(1L, raw);
    }

    @Test
    void noPhysicalForeignKeys_onCourseTables() {
        Integer fkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                        + "WHERE CONSTRAINT_TYPE='FOREIGN KEY' AND TABLE_SCHEMA='yunsie_platform' AND TABLE_NAME IN "
                        + "('course_course','course_chapter','course_lesson','course_lesson_knowledge_node','course_learn_progress')",
                Integer.class);
        assertEquals(0, fkCount);
    }

    // ---------- helpers ----------

    private Long adminId() {
        return 2L; // 种子 admin 账号（V3）
    }

    private long createCourse(String token, Long teacherId, String title) throws Exception {
        return dataId(postJson("/api/v1/course/courses", token,
                Map.of("certificateId", certId, "teacherId", teacherId, "title", title, "description", "")));
    }

    private long createChapter(String token, long courseId, String title) throws Exception {
        return dataId(postJson("/api/v1/course/chapters", token,
                Map.of("courseId", courseId, "title", title, "sort", 0)));
    }

    private long createLesson(String token, long chapterId, String title, int duration) throws Exception {
        return dataId(postJson("/api/v1/course/lessons", token,
                Map.of("chapterId", chapterId, "title", title, "durationSeconds", duration, "sort", 0)));
    }

    /** 建课+章+节+发布，返回课程 ID */
    private long publishCourse(String token, String title, int lessonCount) throws Exception {
        long courseId = createCourse(token, adminId(), title);
        long chapterId = createChapter(token, courseId, "章节");
        for (int i = 0; i < lessonCount; i++) {
            createLesson(token, chapterId, "小节" + i, 600);
        }
        mockMvc.perform(post("/api/v1/course/courses/{id}/publish", courseId).header(AUTH, bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        return courseId;
    }

    private long lessonIdOf(long courseId) {
        Course course = courseMapper.selectById(courseId);
        return lessonMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseLesson>()
                        .exists("SELECT 1 FROM course_chapter ch WHERE ch.id = course_lesson.chapter_id AND ch.course_id = " + course.getId())
                        .orderByAsc(CourseLesson::getId))
                .get(0).getId();
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
