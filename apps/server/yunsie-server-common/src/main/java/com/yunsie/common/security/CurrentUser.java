package com.yunsie.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 当前登录用户 ID 参数注解。
 * 由 boot 的 CurrentUserArgumentResolver 从 SecurityContext 解析（Long userId）。
 * 业务代码禁止自行读取 SecurityContext —— 统一经本注解注入，
 * 数据范围校验在域 Service 内完成（permission-rbac：禁止散落权限判断）。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}
