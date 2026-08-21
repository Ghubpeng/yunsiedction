package com.yunsie.boot.security;

import com.yunsie.common.security.TokenService;
import com.yunsie.module.sys.api.SysAuthApi;
import com.yunsie.module.user.api.UserAuthApi;
import com.yunsie.module.user.enums.UserStatus;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器（无状态，access token 不含权限列表 ——
 * 每次请求由服务端重新解析 用户→角色→权限，permission-rbac）。
 * 解析失败/用户禁用：不设置认证信息，由统一入口点返回 401 + 10002。
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 标记 token 无效（区别于"未携带 token"），供入口点选择错误码 */
    public static final String ATTR_INVALID_TOKEN = "yunsie.auth.invalidToken";

    private final TokenService tokenService;
    private final UserAuthApi userAuthApi;
    private final SysAuthApi sysAuthApi;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header == null || !header.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }
            String token = header.substring(7);
            TokenService.AccessClaims claims = tokenService.parseAccessToken(token);
            UserAuthApi.UserAuthView user = userAuthApi.findById(claims.userId());
            if (user == null || user.status() == null || user.status() != UserStatus.NORMAL.code()) {
                request.setAttribute(ATTR_INVALID_TOKEN, Boolean.TRUE);
                filterChain.doFilter(request, response);
                return;
            }
            List<SimpleGrantedAuthority> authorities = sysAuthApi.listPermissionCodes(claims.userId())
                    .stream().map(SimpleGrantedAuthority::new).toList();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(claims.userId(), null, authorities);
            authentication.setDetails(user);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
            request.setAttribute(ATTR_INVALID_TOKEN, Boolean.TRUE);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
