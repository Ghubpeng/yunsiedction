package com.yunsie.boot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.certificate.entity.CertificateCategory;
import com.yunsie.module.certificate.entity.Certificate;
import com.yunsie.module.certificate.mapper.CertificateCategoryMapper;
import com.yunsie.module.certificate.mapper.CertificateMapper;
import com.yunsie.module.subject.api.SubjectQueryApi;
import com.yunsie.module.subject.entity.ExamSubject;
import com.yunsie.module.subject.entity.KnowledgeNode;
import com.yunsie.module.subject.entity.SubjectVersion;
import com.yunsie.module.subject.enums.NodeType;
import com.yunsie.module.subject.enums.VersionStatus;
import com.yunsie.module.subject.mapper.ExamSubjectMapper;
import com.yunsie.module.subject.mapper.KnowledgeNodeMapper;
import com.yunsie.module.subject.mapper.SubjectVersionMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Certificate + Subject 集成测试（真实 MySQL + 完整过滤链，事务回滚不留数据）。
 * 覆盖：V4 种子 / CRUD / 编码唯一 / 树移动 path·level 级联 / 移动·删除保护 /
 * 五级知识体系 / 版本状态 / RBAC 403 / 授权管理员 / 公开只读过滤 / 跨域契约 /
 * 无物理外键 / 逻辑删除。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CertificateSubjectIntegrationTest {

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
    private CertificateCategoryMapper categoryMapper;
    @Autowired
    private CertificateMapper certificateMapper;
    @Autowired
    private ExamSubjectMapper subjectMapper;
    @Autowired
    private SubjectVersionMapper versionMapper;
    @Autowired
    private KnowledgeNodeMapper nodeMapper;
    @Autowired
    private CertificateQueryApi certificateQueryApi;
    @Autowired
    private SubjectQueryApi subjectQueryApi;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private Long normalUserId;

    @BeforeEach
    void setup() throws Exception {
        adminToken = login("admin", "Admin@123456");
        normalUserId = userService.create(new UserCreateReq("it_learner", null, "学员", "Learner@1234", 1, null));
    }

    // ---------- 基础 ----------

    @Test
    void v4_permissionSeeds_exist() {
        SysPermission p = permissionMapper.selectOne(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermissionCode, "certificate:category:create"));
        assertNotNull(p, "V4 权限点种子缺失");
        assertEquals(1, p.getStatus());
    }

    @Test
    void noPhysicalForeignKeys_onNewTables() {
        Integer fkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                        + "WHERE CONSTRAINT_TYPE='FOREIGN KEY' AND TABLE_SCHEMA='yunsie_platform' AND TABLE_NAME IN "
                        + "('certificate_category','certificate_cert','subject_subject','subject_version','subject_knowledge_node')",
                Integer.class);
        assertEquals(0, fkCount, "新表不得有物理外键（database-design）");
    }

    // ---------- category ----------

    @Test
    void category_crud_viaAdminApi() throws Exception {
        long id = createCategory("nurse", 0L);
        mockMvc.perform(get("/api/v1/certificate/categories/tree").header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[?(@.id==" + id + ")]").exists());
        mockMvc.perform(put("/api/v1/certificate/categories/{id}", id).header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "护理类", "enabled", 1, "description", "updated"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/v1/certificate/categories/{id}", id).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void category_duplicateCode_30101() throws Exception {
        createCategory("dup", 0L);
        mockMvc.perform(post("/api/v1/certificate/categories").header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("parentId", 0L, "name", "重复", "code", "dup"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30101));
    }

    @Test
    void category_deleteWithChildren_30103() throws Exception {
        long a = createCategory("a", 0L);
        createCategory("b", a);
        mockMvc.perform(delete("/api/v1/certificate/categories/{id}", a).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30103));
    }

    @Test
    void category_move_cascadesPathAndLevel() throws Exception {
        long a = createCategory("ma", 0L);
        long b = createCategory("mb", a);
        long c = createCategory("mc", b);
        long d = createCategory("md", a);
        // A/B/C 移到 D 下：A → D → B → C
        mockMvc.perform(post("/api/v1/certificate/categories/{id}/move", b).header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newParentId", d, "sort", 0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        CertificateCategory bNode = categoryMapper.selectById(b);
        CertificateCategory cNode = categoryMapper.selectById(c);
        assertEquals(d, bNode.getParentId());
        assertEquals("/" + a + "/" + d + "/" + b + "/", bNode.getPath());
        assertEquals(3, bNode.getLevel());
        assertEquals("/" + a + "/" + d + "/" + b + "/" + c + "/", cNode.getPath());
        assertEquals(4, cNode.getLevel());
    }

    @Test
    void category_move_intoDescendant_30105() throws Exception {
        long a = createCategory("xa", 0L);
        long b = createCategory("xb", a);
        createCategory("xc", b);
        mockMvc.perform(post("/api/v1/certificate/categories/{id}/move", b).header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newParentId", b, "sort", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30105));
    }

    // ---------- certificate ----------

    @Test
    void certificate_crud_and_duplicateCode() throws Exception {
        long categoryId = createCategory("c1", 0L);
        long certId = createCertificate("nurse-license", categoryId);
        mockMvc.perform(get("/api/v1/certificate/certificates/{id}", certId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("护士执业资格考试"));
        mockMvc.perform(post("/api/v1/certificate/certificates").header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("categoryId", categoryId, "name", "重复证书", "code", "nurse-license"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30106));
        mockMvc.perform(put("/api/v1/certificate/certificates/{id}", certId).header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "护士执业资格考试(新版)", "enabled", 1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/v1/certificate/certificates/{id}", certId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void certificate_delete_blockedBySubject_30204() throws Exception {
        long categoryId = createCategory("c2", 0L);
        long certId = createCertificate("nurse-blocked", categoryId);
        createSubject(certId, "subj-x");
        mockMvc.perform(delete("/api/v1/certificate/certificates/{id}", certId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30204));
        assertNotNull(certificateMapper.selectById(certId), "完整性否决后证书必须仍存在");
    }

    // ---------- subject / version / node ----------

    @Test
    void knowledgeChain_fullFiveLevelTree() throws Exception {
        Chain chain = buildChain("chain1");
        MvcResult result = mockMvc.perform(get("/api/v1/subject/nodes/tree")
                        .param("versionId", String.valueOf(chain.versionId))
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].subjectId").value(chain.subjectId))
                .andExpect(jsonPath("$.data[0].children[0].nodeType").value(1))
                .andExpect(jsonPath("$.data[0].children[0].children[0].nodeType").value(2))
                .andExpect(jsonPath("$.data[0].children[0].children[0].children[0].nodeType").value(3))
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("\"level\":1"));
    }

    @Test
    void node_move_cascadesPathAndLevel() throws Exception {
        Chain chain = buildChain("chain2");
        long chapter2 = createNode(chain, 0L, NodeType.CHAPTER.code(), "chapter-2");
        mockMvc.perform(post("/api/v1/subject/nodes/{id}/move", chain.kpId).header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newParentId", chapter2, "sort", 0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        KnowledgeNode kp = nodeMapper.selectById(chain.kpId);
        KnowledgeNode child = nodeMapper.selectById(chain.childId);
        assertEquals(chapter2, kp.getParentId());
        assertEquals("/" + chapter2 + "/" + chain.kpId + "/", kp.getPath());
        assertEquals(2, kp.getLevel());
        assertEquals("/" + chapter2 + "/" + chain.kpId + "/" + chain.childId + "/", child.getPath());
        assertEquals(3, child.getLevel());
    }

    @Test
    void node_move_intoDescendant_30212() throws Exception {
        Chain chain = buildChain("chain3");
        mockMvc.perform(post("/api/v1/subject/nodes/{id}/move", chain.kpId).header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newParentId", chain.childId, "sort", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30212));
    }

    @Test
    void node_parentType_invalid_30213() throws Exception {
        Chain chain = buildChain("chain4");
        mockMvc.perform(post("/api/v1/subject/nodes").header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("versionId", chain.versionId, "subjectId", chain.subjectId,
                                "parentId", chain.childId, "nodeType", NodeType.KNOWLEDGE_POINT.code(),
                                "name", "非法层级", "code", "invalid-kp"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30213));
    }

    @Test
    void node_delete_withChildren_30211() throws Exception {
        Chain chain = buildChain("chain5");
        mockMvc.perform(delete("/api/v1/subject/nodes/{id}", chain.chapterId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30211));
    }

    @Test
    void version_setCurrent_archivesPrevious() throws Exception {
        long categoryId = createCategory("v1", 0L);
        long certId = createCertificate("nurse-ver", categoryId);
        long v1 = createVersion(certId, "2025");
        long v2 = createVersion(certId, "2026");
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", v1).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", v2).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertEquals(VersionStatus.ARCHIVED.code(), versionMapper.selectById(v1).getStatus());
        assertEquals(VersionStatus.CURRENT.code(), versionMapper.selectById(v2).getStatus());
        assertEquals(v2, subjectQueryApi.findCurrentVersionId(certId));
    }

    @Test
    void version_delete_withNodes_30207() throws Exception {
        Chain chain = buildChain("chain6");
        mockMvc.perform(delete("/api/v1/subject/versions/{id}", chain.versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30207));
    }

    // ---------- RBAC / 公开读取 ----------

    @Test
    void normalUser_cannotModify_403() throws Exception {
        String token = login("it_learner", "Learner@1234");
        mockMvc.perform(post("/api/v1/certificate/categories").header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("parentId", 0L, "name", "越权", "code", "forbidden"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(20001));
        mockMvc.perform(post("/api/v1/subject/subjects").header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("certificateId", 1L, "name", "越权", "code", "forbidden-subj"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    void operator_withCategoryCreatePermission_canCreate() throws Exception {
        Long roleId = roleService.create(new RoleCreateReq("it_cert_op", "证书运营", 2, 1, "", 0));
        SysPermission perm = permissionMapper.selectOne(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermissionCode, "certificate:category:create"));
        roleService.assignPermissions(roleId, List.of(perm.getId()));
        Long operatorId = userService.create(new UserCreateReq("it_cert_operator", null, "运营", "Operator@1234", 3, null));
        sysUserRoleApi.assignRoles(operatorId, List.of(roleId));
        String token = login("it_cert_operator", "Operator@1234");

        mockMvc.perform(post("/api/v1/certificate/categories").header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("parentId", 0L, "name", "授权创建", "code", "granted"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        // 未授权的操作仍然被拒
        mockMvc.perform(post("/api/v1/certificate/certificates").header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("categoryId", 1L, "name", "无权限", "code", "no-perm"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    void publicTree_hidesDisabledNodes() throws Exception {
        Chain chain = buildChain("chain7");
        // 禁用章节
        mockMvc.perform(put("/api/v1/subject/nodes/{id}", chain.chapterId).header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "基础护理", "enabled", 0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        String token = login("it_learner", "Learner@1234");
        MvcResult pub = mockMvc.perform(get("/api/v1/subject/public/tree")
                        .param("certificateId", String.valueOf(chain.certificateId))
                        .header(AUTH, bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].children.length()").value(0))
                .andReturn();
        assertTrue(pub.getResponse().getContentAsString().contains("护理学基础"));
        // 管理树仍包含禁用节点
        mockMvc.perform(get("/api/v1/subject/nodes/tree")
                        .param("versionId", String.valueOf(chain.versionId))
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].children.length()").value(1));
    }

    @Test
    void publicCertificateEndpoints_authenticatedOnly() throws Exception {
        mockMvc.perform(get("/api/v1/certificate/public/categories/tree"))
                .andExpect(status().isUnauthorized());
        String token = login("it_learner", "Learner@1234");
        mockMvc.perform(get("/api/v1/certificate/public/categories/tree").header(AUTH, bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    // ---------- 跨域契约 / 逻辑删除 ----------

    @Test
    void crossDomain_contracts_work() throws Exception {
        Chain chain = buildChain("chain8");
        CertificateQueryApi.CertificateView certView = certificateQueryApi.findCertificate(chain.certificateId);
        assertEquals("nurse-cert", certView.code());
        assertTrue(certificateQueryApi.existsEnabled(chain.certificateId));
        SubjectQueryApi.KnowledgeNodeView nodeView = subjectQueryApi.findNode(chain.kpId);
        assertEquals(NodeType.KNOWLEDGE_POINT.code(), nodeView.nodeType());
        assertEquals(chain.versionId, subjectQueryApi.findCurrentVersionId(chain.certificateId));
    }

    @Test
    void logicDelete_effective() throws Exception {
        long categoryId = createCategory("ld", 0L);
        mockMvc.perform(delete("/api/v1/certificate/categories/{id}", categoryId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        assertNull(categoryMapper.selectById(categoryId), "逻辑删除后 MyBatis 查询不可见");
        Long raw = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM certificate_category WHERE id=? AND deleted=1", Long.class, categoryId);
        assertEquals(1L, raw, "物理行保留且 deleted=1");
    }

    // ---------- helpers ----------

    private record Chain(long certificateId, long subjectId, long versionId,
                         long chapterId, long kpId, long childId) {
    }

    private Chain buildChain(String suffix) throws Exception {
        long categoryId = createCategory("cat-" + suffix, 0L);
        long certId = createCertificate("nurse-cert", categoryId);
        long subjectId = createSubject(certId, "subj-" + suffix);
        long versionId = createVersion(certId, "2026");
        mockMvc.perform(put("/api/v1/subject/versions/{id}/current", versionId).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        long chapterId = createNode(versionId, subjectId, 0L, NodeType.CHAPTER.code(), "chapter-" + suffix);
        long kpId = createNode(versionId, subjectId, chapterId, NodeType.KNOWLEDGE_POINT.code(), "kp-" + suffix);
        long childId = createNode(versionId, subjectId, kpId, NodeType.CHILD_KNOWLEDGE_POINT.code(), "child-" + suffix);
        return new Chain(certId, subjectId, versionId, chapterId, kpId, childId);
    }

    private long createCategory(String code, Long parentId) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/certificate/categories").header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("parentId", parentId, "name", "分类-" + code, "code", code))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return dataId(r);
    }

    private long createCertificate(String code, Long categoryId) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/certificate/certificates").header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("categoryId", categoryId, "name", "护士执业资格考试", "code", code))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return dataId(r);
    }

    private long createSubject(long certificateId, String code) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/subject/subjects").header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("certificateId", certificateId, "name", "护理学基础", "code", code))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return dataId(r);
    }

    private long createVersion(long certificateId, String versionNo) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/subject/versions").header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("certificateId", certificateId, "versionNo", versionNo,
                                "name", versionNo + "版大纲"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return dataId(r);
    }

    private long createNode(long versionId, long subjectId, long parentId, int nodeType, String code) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/subject/nodes").header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("versionId", versionId, "subjectId", subjectId,
                                "parentId", parentId, "nodeType", nodeType, "name", "节点-" + code, "code", code))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return dataId(r);
    }

    private long createNode(Chain chain, long parentId, int nodeType, String code) throws Exception {
        return createNode(chain.versionId, chain.subjectId, parentId, nodeType, code);
    }

    private long dataId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
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
