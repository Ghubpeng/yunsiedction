/**
 * ai 域：AI 学习助手（平台核心壁垒）。
 *
 * <p>MVP 仅 3 个场景：course.chat（章节问答总结）、question.help（题目解析）、note.summarize（笔记整理）。
 * AI 是学习过程中的助手，不是独立聊天机器人。</p>
 *
 * <p>本域职责（Java 侧）：
 * <ol>
 *   <li>请求上下文组装 ContextBuilder：前端只传场景锚点（scene + resourceId），禁止前端拼 prompt。</li>
 *   <li>Token 账务：预检→预扣→按实际 usage 结算→流水（幂等键、条件更新防负余额）。</li>
 *   <li>场景→模型→Provider 路由：配置化（后台可配），请求时下发给 AI 服务执行。</li>
 *   <li>调用 FastAPI AI 服务（内网 + 服务间 secret），流式转发与异常退款。</li>
 * </ol></p>
 *
 * <p>官方知识体系与用户学习数据分离：上下文注入只取最小必要字段，按用户隔离。</p>
 *
 * <p>模块边界规则同 sys 域。</p>
 */
package com.yunsie.module.ai;
