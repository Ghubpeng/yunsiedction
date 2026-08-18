---
name: backend-development
description: Java/Spring Boot 后端开发规范。开发任何后端接口、Service、事务、缓存、定时任务、与 AI 服务集成时使用；数据模型见 database-design，接口契约见 api-design。
---

# backend-development（Java / Spring Boot 后端规范）

## 1. 定位

主业务后端的工程纪律：分层、事务、缓存、安全、幂等、与 AI 服务的集成。技术栈锁定见 `project-architecture`。

## 2. 技术基线

- Java 17+，Spring Boot 3.x，Maven 单仓库多模块（按业务域拆分，见 `project-architecture`）。
- ORM：MyBatis-Plus（决策点 #2）。
- 权限：Spring Security + RBAC 自定义（`permission-rbac`）。
- 校验：Bean Validation（JSR-380），禁止只靠前端校验。

## 3. 分层与命名（强制）

```
Controller（入参校验、DTO/VO 转换、@PreAuthorize 权限注解）
   → Service 接口 + ServiceImpl（事务边界、业务规则编排）
      → Mapper（单表/明确范围的查询，禁止跨域逻辑）
```

- DTO（入参）/ VO（出参）/ Entity 三者分离；禁止用 Entity 直接接参与出参。
- 统一响应体 `Result<T>`：`{ code, message, data, traceId, timestamp }`（`api-design`）。
- 全局异常处理器 + 业务异常体系；禁止吞异常、禁止 `catch(Exception){}` 空处理。
- traceId：MDC 注入，日志与响应统一携带，贯穿后端与 AI 服务调用。

## 4. 事务与并发（强制）

1. `@Transactional` 只放在 Service 层；注意自调用/非 public/异常类型导致失效的坑。
2. 计费、扣款、库存、交卷类更新用**条件更新防并发**：
   ```java
   update wallet set balance = balance - ? where user_id = ? and balance >= ?
   ```
   受影响行数为 0 → 视为余额不足/冲突，回滚并返回业务错误。
3. 涉及金额/Tokens 的操作必须走幂等（`token-billing`、`payment-wallet`）。
4. 禁止在事务里调用外部网络（支付回调、AI 调用先出事务）；外部调用结果通过状态字段与重试机制衔接。

## 5. 缓存规范

- Cache-Aside：先查缓存 → 未命中查库并回填；更新顺序 = **先改库，后删缓存**。
- key 规范：`domain:entity:id`（如 `course:chapter:123`）；TTL 明确；禁止缓存用户余额/权限等强一致数据（用短 TTL 或直查）。
- 防护：穿透（空值缓存）、击穿（互斥/逻辑过期）、雪崩（TTL 加随机）。
- 分布式锁只用于短临界区（如防重复交卷、防重复回调），必须有超时与解锁校验。

## 6. 安全（强制）

- 参数化 SQL 防注入；禁止字符串拼接 SQL。
- 越权校验必须在服务端：资源归属校验（A 用户访问 B 资源 → 403），统一 DataScope（`permission-rbac`）。
- 上传校验：类型白名单、大小限制、内容检测；文件走对象存储，禁止落盘到应用目录长期保存。
- 敏感信息：密码哈希（bcrypt+）、手机号等脱敏展示、密钥进配置中心/环境变量。
- 防刷：登录、验证码、AI 接口的频控（结合 `token-billing` 的预检）。

## 7. 与 AI 服务集成（强制）

- 后端经内网 API 调用 FastAPI 服务；服务间鉴权头（secret），不暴露 AI 服务到公网。
- 调用前：组装场景上下文（`ai-tutor`）→ 计费预检/预扣（`token-billing`）。
- 调用中：流式转发（SSE）；超时、断连、部分输出的处理与计费结算规则见 `token-billing`。
- 调用后：结算、写流水、异常退款（`token-billing`）。
- 重试必须带幂等键；AI 调用失败不吞异常，走统一失败处理（退款+告警）。

## 8. 禁止事项（NEVER）

- Controller 写业务逻辑；Service 写 SQL 拼串。
- 循环里查库（用批量/集合查询）；`SELECT *`。
- 硬编码密钥/IP/金额/单价；金额用 `double/float`。
- 事务里调用外部服务；裸 `new Thread`（用线程池）；无超时的同步等待。
- 支付/计费/交卷接口不幂等。

## 9. 检查清单

- [ ] 分层正确、DTO/VO/Entity 分离？
- [ ] 事务边界正确、并发更新用条件更新？
- [ ] 缓存 key 规范、先改库后删缓存？
- [ ] 越权校验在服务端？SQL 参数化？
- [ ] 涉及金额/计费的接口幂等且写流水？
