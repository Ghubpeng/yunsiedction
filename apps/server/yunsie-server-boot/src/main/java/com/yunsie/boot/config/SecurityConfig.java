package com.yunsie.boot.config;

import com.yunsie.boot.security.JwtAuthenticationFilter;
import com.yunsie.boot.security.RestAccessDeniedHandler;
import com.yunsie.boot.security.RestAuthenticationEntryPoint;
import com.yunsie.common.security.TokenService;
import com.yunsie.module.sys.api.SysAuthApi;
import com.yunsie.module.user.api.UserAuthApi;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置（Stage 1.1 认证与权限闭环）：
 * - 无状态 JWT；除健康检查/登录/刷新/文档外，一律需要认证（默认拒绝）。
 * - 权限点校验统一走 {@code @PreAuthorize("@perm.has('...')")}（PermissionChecker），
 *   通配 *:*:* 恒通过（超级管理员）。
 * - 认证失败 401（10001/10002），授权失败 403（20001/20002）。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TokenService tokenService;
    private final UserAuthApi userAuthApi;
    private final SysAuthApi sysAuthApi;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // 明确放行的公开接口（health / 认证入口 / 公开课程浏览）；其余默认需要认证
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/user/auth/login", "/api/v1/user/auth/refresh").permitAll()
                        // Demo 一键登录（端点存在性由 yunsie.demo.enabled 门控；关闭时该路径无映射，仍 401）
                        .requestMatchers("/api/v1/user/auth/demo-login").permitAll()
                        // 公开课程浏览（列表/大纲；播放凭证与进度仍需要认证——个人数据不公开）
                        .requestMatchers(HttpMethod.GET, "/api/v1/course/public/**").permitAll()
                        // 联系客服公开读取（登录页帮助入口可用；仅联系方式配置，无敏感数据）
                        .requestMatchers(HttpMethod.GET, "/api/v1/service/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(tokenService, userAuthApi, sysAuthApi),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
