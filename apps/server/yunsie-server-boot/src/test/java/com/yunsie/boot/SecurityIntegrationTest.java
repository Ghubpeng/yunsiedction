package com.yunsie.boot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunsie.common.security.TokenService;
import com.yunsie.module.sys.api.SysUserRoleApi;
import com.yunsie.module.sys.dto.RoleCreateReq;
import com.yunsie.module.sys.entity.SysPermission;
import com.yunsie.module.sys.mapper.SysPermissionMapper;
import com.yunsie.module.sys.service.SysRoleService;
import com.yunsie.module.user.api.UserAuthApi;
import com.yunsie.module.user.dto.UserCreateReq;
import com.yunsie.module.user.service.UserService;
import com.yunsie.module.user.vo.LoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 安全集成测试（真实 MySQL + 完整过滤链，事务回滚不留数据）：
 * 覆盖 A 登录成功 / B 密码错误 / C 用户不存在 / D 未登录 401 /
 * E 无权限 403 / F 授权访问 / G 通配权限 / H·I 横向越权 / J 非法·过期 token /
 * K traceId 贯穿 + 刷新轮换 + 登出撤销。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest {

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
    private UserAuthApi userAuthApi;
    @Value("${yunsie.jwt.secret}")
    private String jwtSecret;

    private Long adminId;
    private Long normalId;

    @BeforeEach
    void setup() {
        adminId = userAuthApi.findByAccount("admin").userId();
        normalId = userService.create(new UserCreateReq("it_normal", null, "normal", "Normal@123456", 1, null));
    }

    // ---------- A/B/C 登录 ----------

    @Test
    void myPermissionCodes_adminHasCodes_normalEmpty() throws Exception {
        // Stage 1.9 追加：/api/v1/sys/permissions/me/codes（管理端菜单按权限渲染）
        String adminToken = login("admin", "Admin@123456");
        mockMvc.perform(get("/api/v1/sys/permissions/me/codes")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").isNumber())
                .andExpect(jsonPath("$.data[0]").isString());
        String normalToken = login("it_normal", "Normal@123456");
        mockMvc.perform(get("/api/v1/sys/permissions/me/codes")
                        .header("Authorization", bearer(normalToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void login_success_returnsTokens() throws Exception {
        String body = json(Map.of("account", "admin", "password", "Admin@123456"));
        MvcResult result = mockMvc.perform(post("/api/v1/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").value(7200))
                .andReturn();
        assertNotEquals("", result.getResponse().getHeader("X-Trace-Id"));
    }

    @Test
    void login_wrongPassword_10003() throws Exception {
        String body = json(Map.of("account", "admin", "password", "WrongPass@123"));
        mockMvc.perform(post("/api/v1/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void login_unknownUser_10003_unified() throws Exception {
        String body = json(Map.of("account", "no_such_user", "password", "Whatever@123"));
        mockMvc.perform(post("/api/v1/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));
    }

    // ---------- D 未登录 ----------

    @Test
    void protectedApi_withoutToken_401_10001() throws Exception {
        mockMvc.perform(get("/api/v1/sys/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10001));
    }

    // ---------- J 非法 / 过期 token ----------

    @Test
    void invalidToken_401_10002() throws Exception {
        mockMvc.perform(get("/api/v1/sys/roles")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    void expiredToken_401_10002() throws Exception {
        TokenService expired = new TokenService(jwtSecret, -5, 60);
        String token = expired.createAccessToken(adminId, "admin");
        mockMvc.perform(get("/api/v1/sys/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002));
    }

    // ---------- E 普通用户无权限 ----------

    @Test
    void normalUser_withoutPermission_403_20001() throws Exception {
        String token = login("it_normal", "Normal@123456");
        mockMvc.perform(get("/api/v1/sys/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20001));
    }

    // ---------- F 授权管理员访问授权 API ----------

    @Test
    void operator_withViewPermission_canListUsers() throws Exception {
        String token = operatorToken();
        mockMvc.perform(get("/api/v1/user/accounts?pageNum=1&pageSize=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void operator_withoutCreatePermission_403_20001() throws Exception {
        String token = operatorToken();
        String body = json(Map.of("username", "x_should_fail", "password", "Whatever@1234"));
        mockMvc.perform(post("/api/v1/user/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20001));
    }

    // ---------- G 总管理员通配权限 ----------

    @Test
    void superAdmin_wildcard_canAccessAnyApi() throws Exception {
        String token = login("admin", "Admin@123456");
        mockMvc.perform(get("/api/v1/sys/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/user/accounts?pageNum=1&pageSize=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ---------- H/I 横向越权（修改 URL userId 不能访问他人数据） ----------

    @Test
    void userA_cannotReadUserBProfile_403_20002() throws Exception {
        String token = login("it_normal", "Normal@123456");
        mockMvc.perform(get("/api/v1/user/profiles/{userId}", adminId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20002));
    }

    @Test
    void userA_cannotModifyUserBProfile_403_20002() throws Exception {
        String token = login("it_normal", "Normal@123456");
        String body = json(Map.of("realName", "hack"));
        mockMvc.perform(put("/api/v1/user/profiles/{userId}", adminId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20002));
    }

    @Test
    void userA_canReadOwnProfile() throws Exception {
        String token = login("it_normal", "Normal@123456");
        mockMvc.perform(get("/api/v1/user/profiles/{userId}", normalId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ---------- 刷新轮换 / 登出撤销 ----------

    @Test
    void refresh_rotates_oldTokenReuseWithinGrace_returnsSamePair() throws Exception {
        LoginVO first = loginFull("admin", "Admin@123456");
        String body = json(Map.of("refreshToken", first.refreshToken()));
        // 第一次刷新：正常轮换
        MvcResult rotate = mockMvc.perform(post("/api/v1/user/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        JsonNode rotated = objectMapper.readTree(rotate.getResponse().getContentAsString()).path("data");
        // 旧 refresh 复用（丢响应重试）：宽限期内幂等返回同一令牌对，不产生新轮换
        mockMvc.perform(post("/api/v1/user/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value(rotated.path("accessToken").asText()))
                .andExpect(jsonPath("$.data.refreshToken").value(rotated.path("refreshToken").asText()));
    }

    @Test
    void logout_revokesRefreshToken() throws Exception {
        LoginVO first = loginFull("admin", "Admin@123456");
        String body = json(Map.of("refreshToken", first.refreshToken()));
        mockMvc.perform(post("/api/v1/user/auth/logout")
                        .header("Authorization", "Bearer " + first.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/user/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10002));
    }

    // ---------- K traceId 贯穿 ----------

    @Test
    void traceId_roundTripsInResponse() throws Exception {
        String token = login("admin", "Admin@123456");
        MvcResult result = mockMvc.perform(get("/api/v1/sys/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String header = result.getResponse().getHeader("X-Trace-Id");
        String bodyTraceId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("traceId").asText();
        assertEquals(header, bodyTraceId);
        assertNotEquals("", header);
    }

    // ---------- helpers ----------

    private String operatorToken() throws Exception {
        Long roleId = roleService.create(new RoleCreateReq("it_operator", "IT操作员", 2, 1, "", 0));
        SysPermission viewPerm = permissionMapper.selectOne(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermissionCode, "sys:user:view"));
        roleService.assignPermissions(roleId, List.of(viewPerm.getId()));
        Long operatorId = userService.create(new UserCreateReq("it_operator_user", null, "op", "Operator@1234", 1, null));
        sysUserRoleApi.assignRoles(operatorId, List.of(roleId));
        return login("it_operator_user", "Operator@1234");
    }

    private String login(String account, String password) throws Exception {
        return loginFull(account, password).accessToken();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private LoginVO loginFull(String account, String password) throws Exception {
        String body = json(Map.of("account", account, "password", password));
        MvcResult result = mockMvc.perform(post("/api/v1/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readValue(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("data").toString(),
                LoginVO.class);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
