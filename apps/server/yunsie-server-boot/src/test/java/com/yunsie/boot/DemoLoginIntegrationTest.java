package com.yunsie.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunsie.module.user.dto.UserCreateReq;
import com.yunsie.module.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Demo 一键登录集成测试（Stage 2.1）。
 * 端点仅在 yunsie.demo.enabled=true 时注册（生产默认关闭，端点不存在）；
 * 签发的是真实令牌，走正常鉴权与权限链路（仅免除输入账号密码）。
 * 使用独立演示账号 it_demo（@TestPropertySource 覆盖），不与 scripts/demo.ps1 播种的 demo_learner 冲突。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {"yunsie.demo.enabled=true", "yunsie.demo.username=it_demo"})
class DemoLoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserService userService;

    @Test
    void demoLogin_missingAccount_10005() throws Exception {
        mockMvc.perform(post("/api/v1/user/auth/demo-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10005));
    }

    @Test
    void demoLogin_issuesRealTokens_usableOnProtectedApi() throws Exception {
        userService.create(new UserCreateReq("it_demo", null, "演示学员", "Demo@123456", 1, null));
        MvcResult result = mockMvc.perform(post("/api/v1/user/auth/demo-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("it_demo"))
                .andReturn();
        String access = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
        // 真实令牌可访问受保护接口（与正常登录完全一致）
        mockMvc.perform(get("/api/v1/user/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("it_demo"));
    }
}
