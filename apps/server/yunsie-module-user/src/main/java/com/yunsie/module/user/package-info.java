/**
 * user 域：学员账号、教师账号（管理员创建+RBAC 授权）、用户资料、会话、学习档案（learning-profile）。
 *
 * <p>学习档案数据（user_* / learn_*）为个人数据资产：采集事件驱动、异步更新、按用户隔离；
 * AI 服务经 Java 侧读取最小必要字段，禁止全量拉取（learning-profile / ai-tutor）。</p>
 *
 * <p>模块边界规则同 sys 域：表只归本域，跨域只走 api 包。</p>
 */
package com.yunsie.module.user;
