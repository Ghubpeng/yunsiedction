package com.yunsie.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunsie.module.user.dto.UserCreateReq;
import com.yunsie.module.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Demo 三角色登录集成测试（Stage 2.1）：默认 learner / role=teacher / 非法 role 参数错误 /
 * role=admin 管理员令牌可访问受保护接口。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "yunsie.demo.enabled=true",
        "yunsie.demo.username=it_demo",
        "yunsie.demo.teacher-username=it_demo_teacher",
        "yunsie.demo.admin-username=admin"
})
class DemoRoleIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserService userService;

    @Test
    void demoLogin_defaultRole_learner() throws Exception {
        userService.create(new UserCreateReq("it_demo", null, "演示学员", "Demo@123456", 1, null));
        mockMvc.perform(post("/api/v1/user/auth/demo-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.username").value("it_demo"));
    }

    @Test
    void demoLogin_teacherRole_teacherAccount() throws Exception {
        userService.create(new UserCreateReq("it_demo_teacher", null, "演示教师", "Teacher@1234", 2, null));
        mockMvc.perform(post("/api/v1/user/auth/demo-login")
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("role", "teacher"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.username").value("it_demo_teacher"));
    }

    @Test
    void demoLogin_invalidRole_paramError() throws Exception {
        mockMvc.perform(post("/api/v1/user/auth/demo-login")
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("role", "boss"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30001));
    }

    @Test
    void demoLogin_adminRole_accessProtectedApi() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/user/auth/demo-login")
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("role", "admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.username").value("admin"))
                .andReturn();
        String access = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
        mockMvc.perform(get("/api/v1/sys/roles").header(AUTH, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
