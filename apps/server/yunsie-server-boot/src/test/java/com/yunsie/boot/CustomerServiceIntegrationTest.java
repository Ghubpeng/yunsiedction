package com.yunsie.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunsie.module.user.dto.UserCreateReq;
import com.yunsie.module.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 2.2：客服配置（配置化，不接第三方客服系统）。
 * 覆盖：匿名公开读取 / 管理员更新 / 公开端反映更新 / 无权限用户 403 / 开关关闭状态。
 * 配置为单行（id=1，V11 种子），测试事务回滚不留痕。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerServiceIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserService userService;

    private String adminToken() throws Exception {
        return login("admin", "Admin@123456");
    }

    private String learnerToken() throws Exception {
        userService.create(new UserCreateReq("it_cs_learner", null, "学员", "Learner@1234", 1, null));
        return login("it_cs_learner", "Learner@1234");
    }

    @Test
    void publicRead_anonymousAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/service/customer-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(1));
    }

    @Test
    void adminUpdate_reflectedInPublicRead() throws Exception {
        String token = adminToken();
        mockMvc.perform(put("/api/v1/sys/customer-service")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "phone", "400-800-1234",
                                "wechat", "yunsie_service",
                                "qrCodeUrl", "https://example.com/qr.png",
                                "serviceTime", "工作日 9:00-21:00",
                                "enabled", 1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.phone").value("400-800-1234"));
        mockMvc.perform(get("/api/v1/service/customer-service"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.phone").value("400-800-1234"))
                .andExpect(jsonPath("$.data.wechat").value("yunsie_service"))
                .andExpect(jsonPath("$.data.serviceTime").value("工作日 9:00-21:00"));
    }

    @Test
    void adminGet_returnsConfig() throws Exception {
        mockMvc.perform(get("/api/v1/sys/customer-service")
                        .header(AUTH, "Bearer " + adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(1));
    }

    @Test
    void learnerWithoutPerm_forbidden() throws Exception {
        mockMvc.perform(put("/api/v1/sys/customer-service")
                        .header(AUTH, "Bearer " + learnerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", 0))))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledConfig_publicReflectsDisabled() throws Exception {
        String token = adminToken();
        mockMvc.perform(put("/api/v1/sys/customer-service")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", "", "wechat", "", "qrCodeUrl", "",
                                "serviceTime", "工作日 9:00-18:00", "enabled", 0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/service/customer-service"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(0));
    }

    private String login(String account, String password) throws Exception {
        var result = mockMvc.perform(post("/api/v1/user/auth/login")
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
