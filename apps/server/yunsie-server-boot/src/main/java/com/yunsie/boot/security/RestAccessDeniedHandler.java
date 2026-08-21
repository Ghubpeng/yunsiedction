package com.yunsie.boot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunsie.common.api.Result;
import com.yunsie.common.error.PermissionErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 统一权限拒绝处理：403 + 20001（无操作权限）。
 * 数据范围越权（20002）由服务层抛 BizException，经 GlobalExceptionHandler 同样返回 403。
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.fail(PermissionErrorCode.NO_PERMISSION));
    }
}
