package com.yunsie.boot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunsie.common.api.Result;
import com.yunsie.common.error.AuthErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 统一认证入口点：未登录 → 401 + 10001；token 无效/过期 → 401 + 10002。
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean invalidToken = Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.ATTR_INVALID_TOKEN));
        AuthErrorCode errorCode = invalidToken ? AuthErrorCode.TOKEN_INVALID : AuthErrorCode.UNAUTHORIZED;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.fail(errorCode));
    }
}
