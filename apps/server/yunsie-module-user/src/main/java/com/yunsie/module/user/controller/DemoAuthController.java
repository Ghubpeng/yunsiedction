package com.yunsie.module.user.controller;

import com.yunsie.common.api.Result;
import com.yunsie.module.user.dto.DemoLoginReq;
import com.yunsie.module.user.service.AuthService;
import com.yunsie.module.user.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo 一键登录端点（仅本地开发/演示环境存在）。
 * 由 yunsie.demo.enabled=true 门控（默认 false，生产构建中该端点不存在）；
 * 签发的是真实 JWT/refresh 令牌，走与正常登录完全一致的鉴权与权限链路——
 * 仅免除「输入演示账号密码」这一步，绝非绕过鉴权。
 * 演示数据由 scripts/demo.ps1 播种（真实 API 创建，含课程视频/考试/档案数据）。
 */
@RestController
@RequestMapping("/api/v1/user/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "yunsie.demo.enabled", havingValue = "true", matchIfMissing = false)
public class DemoAuthController {

    private final AuthService authService;

    @PostMapping("/demo-login")
    public Result<LoginVO> demoLogin(@RequestBody(required = false) DemoLoginReq req) {
        String role = req == null ? null : req.role();
        return Result.ok(authService.demoLogin(role));
    }
}
