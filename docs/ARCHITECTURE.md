# Stage 1 工程骨架 · 架构说明

> 状态：Stage 1 骨架（无业务代码）。本文档记录骨架期的架构决策与约束，随开发演进更新。
> 唯一权威：`skills/`。本文档与 Skill 冲突时以 Skill 为准；PRD 与 Skill 冲突的记录见 `docs/CONFLICTS.md`。

## 1. 架构总览

```
Web(React+Vite+TS, 响应式含H5)          ← Stage 1 唯一端（已确认决策）
        │ /api/v1（统一响应体/错误码，api-design）
        ▼
Spring Boot 3.5 模块化单体（Java 21 / Maven）          ← 非微服务（已确认决策）
  boot(装配) + common(基础设施) + 11 业务域模块
  职责：身份/权限/业务上下文/Token 账务/订单/业务数据/AI 调用计费/AI 请求上下文组装
        │ 内网 + 服务间 secret（AI 服务不暴露公网）
        ▼
FastAPI AI 服务（Python）
  职责：LLM 调用 / RAG / AI 推理 / AI 知识处理 / AI Provider 适配（Provider 可替换）
        │
MySQL 8.4 ── Redis 7.4 ── MinIO(本地对象存储)
向量检索：MVP 轻量（AI 服务内关键词索引），接口预留 embedding 升级；不引入独立向量库
```

**不引入**（已确认决策）：MQ / Nacos / Prometheus / Grafana / 微服务集群。
**预留扩展点**（不实现业务）：直播（course 类型预留）、代理分佣（promo 域占位）、
外部通知渠道（notify 渠道抽象）、AI 课程自动处理流水线、正式考试模式。

## 2. 模块化单体（apps/server，14 个 Maven 模块）

| 模块 | 域职责 | 依赖方向（只依赖 api 包） |
|---|---|---|
| `yunsie-server-common` | 统一响应体/错误码/业务异常/分页（零依赖） | 被所有模块依赖 |
| `yunsie-server-boot` | 装配：启动类、application.yml、Flyway 迁移目录 | 依赖全部模块（唯一允许） |
| `yunsie-module-sys` | RBAC（角色/菜单/权限点/DataScope）、字典、审计 | 叶子 |
| `yunsie-module-user` | 学员/教师账号、资料、会话、学习档案 | → sys |
| `yunsie-module-certificate` | 证书无限级分类树（通用树，不写死任何证书） | 叶子 |
| `yunsie-module-subject` | 官方知识体系：证书→考试科目→章节→知识点→子知识点（版本/来源） | → certificate |
| `yunsie-module-course` | 商业课程（录播/章节/小节/进度/续播）；课程≠考试科目 | → user, subject, certificate |
| `yunsie-module-question` | 题库/错题/审核流/版本快照/Excel 导入 | → user, subject, certificate |
| `yunsie-module-exam` | 组卷/考试会话/交卷幂等/判分/成绩（快照不可变） | → user, question, subject |
| `yunsie-module-learning-profile` | 学习档案：历史汇总/知识点掌握度(版本隔离)/薄弱点/学习日历/考试预测；消费式叶子域（CONFLICTS #17） | → user, sys, certificate, subject, question, exam, course |
| `yunsie-module-ai` | AI 3 场景入口、ContextBuilder、Token 账务、场景→模型→Provider 路由、AI 网关 | → user, subject, course, question, certificate |
| `yunsie-module-pay` | 平台币钱包、订单、微信/支付宝渠道抽象、VIP（购买+有效期+权益配置） | → user, ai（平台币购 Token 入账） |
| `yunsie-module-notify` | 站内消息（成绩通知/考试开考提醒）；外部渠道抽象预留 | → user, exam |
| `yunsie-module-promo` | 邀请码/注册奖励（配置化）；代理扩展点占位 | → user, pay |

### 边界铁律（骨架已按此搭建）

1. 业务域之间**只允许经对方 `api` 包**（Application Service 接口 + DTO）交互；
   禁止注入其他域 Service 实现类 / Mapper / Entity。
2. 每个业务域的数据库表**只归本域 Mapper 访问**（表前缀=域前缀，database-design）。
3. `boot` 是唯一聚合点；`common` 不含业务。
4. 依赖方向无环（Maven 依赖已按上表声明）。
5. 约束当前靠**结构与评审**保证；ArchUnit 依赖检查测试已落地（Stage 1.6，`ArchitectureTest`：common 零业务依赖 / learning-profile 仅经 api 包访问他域 / 业务域间禁止跨域内部包访问 / 无反向依赖）。

## 3. 关键设计决策（骨架期）

| # | 决策 | 依据 |
|---|---|---|
| D1 | 模块化单体，非微服务；模块边界=未来拆分边界 | 已确认决策 + 5000 用户起步/低成本 |
| D2 | 双服务：Java 主服务 + FastAPI AI 服务 | project-architecture 锁定；Python AI 生态 |
| D3 | 平台币与 Token 双账户；用户可用平台币购买 Token | 已确认决策；pay 域钱包 + ai 域 Token 账务 |
| D4 | 场景→模型→Provider 路由：配置存 Java（ai 域），请求时下发给 AI 服务执行 | 已确认决策 + AI 服务保持无状态 |
| D5 | 官方知识体系（subject 域）与用户学习数据（user 域）分离；课程≠考试科目 | 已确认决策 |
| D6 | 证书无限级树：邻接表 + 物化路径 + 排序 + 启用禁用 | 已确认决策（表结构 Stage 1 实现） |
| D7 | 开发期 Java Server / Web 不进 Compose；Compose 只跑 MySQL/Redis/MinIO；ai-service 用 profile 按需 | G4（本地热更新效率） |
| D8 | Flyway 从第一天启用，V1 为基线；业务表 V2 起 | database-design 强制 |
| D9 | 版本锁：Boot 3.5.3 / MyBatis-Plus 3.5.12 / springdoc 2.8.9 / Java 21 | 已验证可构建 |
| D10 | 统一响应 `{code,message,data,traceId,timestamp}`；错误码码段 1xxxx~5xxxx；`/api/v1/{domain}` | api-design |
| D11 | Stage 2.0 交付基线：server/web 多阶段镜像（非 root、JVM 经 JAVA_OPTS、Flyway 随应用）；nginx 内置于 web 镜像（应用镜像层，非新基础设施）；MinIO 预签名分端点（S3 签名含 Host → `yunsie.minio.public-endpoint` + 专用 `minioPresignClient`）；健康检查全部真实探活（禁 docker ps）；一键脚本 .ps1（build/up/down/health） | Stage 2.0 指令 + CONFLICTS #24；D7 开发期本地热更新工作流保持不变 |

## 4. AI 服务（apps/ai-service）结构

```
app/
  main.py            应用工厂（只挂路由）
  config.py          环境变量配置（AI_ 前缀；密钥不落仓库）
  routers/health.py  健康检查
  core/llm/          Provider 抽象 + 注册表（Provider 可替换，不写死厂商）
  core/rag/          Retriever 抽象（MVP 关键词索引；引用溯源字段：source_type/source_id/kp_id/version）
  providers/         OpenAI 兼容协议 Provider 骨架（DeepSeek/通义等统一入口；Stage 1 未实现 HTTP）
tests/test_health.py 依赖安装后运行（本阶段不安装依赖）
```

- Java → AI 服务：请求携带 `scene` + 已解析的 `model` + `provider` + 组装好的上下文 + 幂等键；
  AI 服务返回 `usage`（input/output tokens），Java 侧按实际 usage 结算（token-billing）。
- 流式（SSE）与断连结算规则在 AI 场景开发阶段按 skills/token-billing 实现。

## 5. 前端（apps/web）

- React 19 + Vite 6 + TS strict；`/api` 代理到 8080；`VITE_API_BASE_URL` 可配置（.env.example）。
- Stage 1.8 用户端 Web MVP 已落地：登录/首页/练习/考试/课程播放续播/学习档案/站内消息；
  分层 pages→features→api→shared（统一 API client：token 注入/401 单飞 refresh/错误码转 ApiError/traceId）。
- Stage 1.9 管理后台 Web MVP 已落地：`/admin` 独立管理端（仪表盘/用户/角色权限/证书分类/知识体系/
  题库审核/Excel 导入/考试组卷发布/课程内容视频/学员档案）；菜单按 `/api/v1/sys/permissions/me/codes`
  权限点渲染（服务端 @PreAuthorize/DataScope 仍为最终权威，CONFLICTS #23）。
- 视觉：Apple 官网式（简洁/高级/留白/克制）；Design Token 一处定义（明暗双套，prefers-color-scheme）；
  自研轻量组件（无大型 UI 库，CONFLICTS #22）；核心学习/考试/视频/档案页无装饰动效。
- 质量：`npm run typecheck`（tsc strict）+ `npm run build`（路由级分包）+ Playwright 浏览器 E2E
  （真实后端 + MySQL + MinIO，独立 e2e_* 数据，setup/teardown 自动清理，31 例全绿）。
- Stage 2.0 生产构建：多阶段 Dockerfile + 生产 nginx（SPA 回退 / `/api/` 反代不缓存 / API 404 不回退 /
  500MB 上传）+ 部署冒烟 `npm run e2e:deploy`（15 例，直打 compose 交付栈，不直连 8080）。
- Stage 2.1 视觉体系（CONFLICTS #25）：用户端重建为编辑式设计系统 `styles/editorial.css`（ed-* 命名空间，
  留白/超大标题/克制导航/黑白灰/大圆角/轻阴影/统一空间尺度/微妙动效/reduced-motion/移动端独立布局）；
  Admin 保持原体系（admin.css + components.css，两套体系并存、互不引用）；首页→课程→视频播放→学习档案
  四页官网级，练习/考试沉浸低干扰；暗色 editorial 后置（亮色优先）。
- Stage 2.1 公开与 Demo（CONFLICTS #25）：匿名公开课程浏览（`GET /api/v1/course/public/**` 放行 +
  outline 匿名大纲，播放凭证与进度仍须登录）；Demo 一键登录为真实鉴权（`POST /api/v1/user/auth/demo-login`
  由 `yunsie.demo.enabled`（默认 false）门控端点存在性，前端按钮由 DEV/VITE_DEMO_ENABLED 构建期门控），
  `scripts/demo.ps1` 经真实管理端 API 幂等播种演示数据。
- Stage 2.1【学习产品化】（CONFLICTS #26）：①用户考试目标 `learn_user_goal`（V10；选择=证书+科目，
  版本由后端解析当前版本；切换=状态流转，历史目标保留）；②内容树归属：course/exam 增 subject_id/version_id
  （课程/考试按目标过滤；前台后台同一套关系）；③练习三层：章节练习（`GET /api/v1/question/practice/chapter/{id}`
  按章节关联知识点取题，判分仍走 question 域）、针对性练习（`/api/v1/learn/me/practice-recommendation`
  基于错题/掌握度/最近练习的真实数据推荐，禁止 random）、模拟考试保持 exam 域；④课程页为学习主线
  （章节 finished 由服务端计算）；⑤「我的」信息架构（目标/我的课程学习状态/我的资产空态不伪造 pay/学习档案/消息）；
  ⑥AI 学习助手体验层（前端协议 `shared/ai/protocol.ts`：5 场景上下文 DTO + AI_READY 唯一开关 + Provider
  unavailable 诚实降级；ai-service 已确认为骨架、无真实 Provider，绝不伪造 LLM）；⑦Demo 登录三角色
  （学员/教师/管理员，端点按 role 解析独立账号，仅演示环境）。

## 6. 验证记录（STEP 10）

| 项 | 结果 |
|---|---|
| Git | 仓库 master；工作区含本次骨架新增文件（未提交，规则 14） |
| Java | openjdk 21.0.12 ✓ |
| Maven | 3.9.16；`mvn -DskipTests install`：13 模块 BUILD SUCCESS ✓ |
| Docker / Compose | 引擎 29.7.2 ✓；`compose config` 校验通过 ✓ |
| Python | 3.14.7；`compileall` 语法检查通过 ✓（依赖未安装，按计划） |
| Node / npm | v24.19.0 / 11.17.0（记录在案；依赖未安装，按计划） |
| 运行时冒烟 | MySQL/Redis/MinIO 容器全部 healthy；`mvn spring-boot:run` 启动成功，Flyway V1 迁移执行，`GET /actuator/health` → HTTP 200 `{"status":"UP"}` ✓（验证后已关闭应用进程，容器保留运行） |

## 7. 下一步（Stage 1 业务开发建议顺序，待你确认后开工）

1. sys 域：RBAC 权限点/角色/管理员（Spring Security + JWT）—— 一切功能的前置。
2. user 域：注册/登录/资料（手机号+验证码方案待确认）。
3. certificate + subject：证书树与知识体系（数据基础）。
4. course：录播课程 + 视频播放凭证（MinIO）。
5. question + exam：题库、练习、模拟考试。
6. pay：平台币/VIP/订单/支付渠道适配（先用沙箱/模拟渠道，禁真实密钥）。
7. ai：3 场景 + RAG + Token 计费链路（先接入一个 Provider）。
8. notify / promo：站内消息 + 邀请奖励。
9. admin 前端（SmartAdmin 风格）+ 用户端 Web 页面（Apple 式视觉）。
10. testing 收尾（计费/权限/交卷/回调必测清单）。

## 8. 未决问题（非阻塞，随时可改）

- 包名/groupId `com.yunsie`、库名 `yunsie_platform`（G1）。
- 手机号验证码短信服务商（外部渠道，MVP 站内优先，待确认）。
- 微信/支付宝渠道的 SDK 选型与沙箱环境（接入时确认）。
- 视频转码方案（云点播 vs 自建 ffmpeg，接入时确认）。

## 9. 部署与交付基线（Stage 2.0）

- 镜像：`apps/server/Dockerfile`（maven:3.9-eclipse-temurin-21 构建 → eclipse-temurin:21-jre-alpine 运行，
  非 root `yunsie`、JVM 参数经 `JAVA_OPTS` 环境变量、Flyway 随应用启动）；`apps/web/Dockerfile`
  （node:24-alpine 构建 → nginx:1.27-alpine，nginx 内置在 web 镜像内，非新基础设施）。
- Compose 应用层（`deploy/docker/compose.yml`）：`server`（依赖 mysql/redis/minio 健康后启动，
  真实 actuator 健康检查）+ `web`（依赖 server 健康）；沿用 dev 基础设施容器与数据卷；
  运行配置一律经 `deploy/docker/.env` 注入（仓库仅 `.env.example`，无真实密钥）。
- 生产 nginx（`apps/web/nginx.conf`）：SPA history 回退（仅文件不存在时）、`/api/` 反代
  （不缓存、404/401 原样 JSON 不回退 index.html）、带 hash 资源 30d immutable、
  `client_max_body_size 500m`（与后端 multipart 500MB 一致）。
- MinIO 预签名分端点（CONFLICTS #24）：S3 签名含 Host → 内网 endpoint 生成的 URL 浏览器不可播；
  `yunsie.minio.public-endpoint`（空=回退 endpoint，本地开发不变）+ 专用 `minioPresignClient`
  （显式 region，预签名仅本地计算、容器内不发起 region 发现请求）。
- 一键脚本（`scripts/`，UTF-8 BOM 兼容 PS 5.1/7）：`build.ps1` / `up.ps1` / `down.ps1`（保数据卷）/
  `health.ps1`（web 首页 / actuator UP / mysqladmin ping / redis-cli ping / minio health /
  `/api/v1` 反代 JSON 链路，全部真实探活，禁 docker ps；关键探针带重试退避）。
- 开发工作流保持不变：Vite dev（/api 代理）+ `mvn spring-boot:run` + `scripts/dev-infra.ps1`（D7）。
- 生产形态冒烟：`apps/web/e2e/deploy.spec.ts`（15 例，独立 `playwright.deploy.config.ts` 直打
  compose 交付栈，不启动任何 dev server，数据经 nginx 反代链路创建/清理）。
- 部署文档：`docs/DEPLOYMENT.md`（快速开始 / 环境变量表 / 服务表 / 运维操作）。
