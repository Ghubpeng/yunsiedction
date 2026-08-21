# Yunsie Learning Platform

面向职业考试的学习平台（成人职业教育培训）：以考试目标驱动，覆盖课程学习、章节练习、题库与考试管理、学习档案、内容运营后台与学习助手（规则版）。

> 首个落地证书：护士执业资格考试（平台化设计，不写死任何证书）。
> 开发规则以 `skills/` 为唯一权威；模块边界与铁律见 `docs/`。

## 技术栈

**Backend**

- Spring Boot 3.5（Java 21 · Maven 模块化单体，14 模块 / 12 业务域）
- MySQL 8.4（Flyway 迁移）
- Redis 7.4
- MinIO（视频对象存储与预签名播放）

**Frontend**

- React 19
- Vite 6
- TypeScript（strict）
- Playwright（浏览器 E2E：dev 57 例 + 部署冒烟 15 例）

**Deployment**

- Docker（多阶段镜像）
- Nginx（生产静态服务 + API 反代）
- Docker Compose（MySQL/Redis/MinIO + server/web 应用层）

## 核心能力

- 考试目标：只选证书即可开始准备，自动生成学习路径（证书→科目→章节→课时）
- 课程学习：录播课程、视频播放、进度保存与续播、章节四态（未开始/学习中/已完成/掌握）
- 章节练习：章节知识点范围取题、服务端判分、得分/掌握度/薄弱点/下一步建议
- 题库管理：题目生命周期（草稿/审核/发布/下架/回收站）、Excel 导入、错题本
- 考试管理：规则组卷、即时判分、模拟考试与成绩
- 学习档案：掌握度/薄弱点/学习日历/周学习报告/通过率预测（规则估算）
- 学习助手：基于真实学习数据的规则推荐（薄弱知识点/推荐课程/推荐练习），无真实 LLM、不伪造 AI
- 内容运营后台：证书体系复制、内容资产统计、运营提醒（待审核题目/空章节/无课程章节）、知识体系与课程内容管理（教师 DataScope）

## 目录结构

```
apps/
  server/          Spring Boot 模块化单体（Java 21 / Maven；14 模块 / 12 业务域）
  ai-service/      FastAPI AI 服务（LLM / RAG / Provider 适配，骨架）
  web/             React + Vite + TS（用户端 + 管理后台 + Playwright E2E）
deploy/docker/     Docker Compose + .env.example（配置经 .env 注入，仓库无真实密钥）
docs/              架构文档 / 冲突台账 / Git 协作规范
scripts/           一键脚本（dev-infra / build / up / down / health / demo）
skills/            AI 编程团队 Skill 规范（唯一权威，勿改动）
```

## 快速开始

```powershell
# 1. 启动本地基础设施（MySQL / Redis / MinIO）
scripts\dev-infra.ps1

# 2. 构建并启动后端
cd apps\server
mvn -DskipTests install
cd yunsie-server-boot
mvn spring-boot:run            # http://localhost:8080/actuator/health

# 3. 前端（开发服务器，/api 代理到 8080）
cd apps\web
npm install
npm run dev                    # http://localhost:5173

# 4. 类型检查与构建
npm run typecheck && npm run build

# 5. 浏览器 E2E（自动启动后端+前端；需 Docker 基础设施）
npx playwright install chromium
npm run e2e
```

### 生产形态一键部署

```powershell
scripts\build.ps1      # 构建交付镜像（server + web）
scripts\up.ps1         # 启动完整应用栈（含七项健康检查）
scripts\demo.ps1       # 播种 Demo 数据（幂等）
cd apps\web; npm run e2e:deploy   # 生产形态冒烟（15 例）
scripts\health.ps1     # 随时复检
scripts\down.ps1       # 停止（数据卷保留）
# 详见 docs/DEPLOYMENT.md；生产环境必须逐项覆盖 deploy/docker/.env（参考 .env.example）
# 注意：YUNSIE_DEMO_ENABLED 生产必须为 false（Demo 入口仅演示环境存在）
```

## 协作

分支与 PR 流程见 [docs/GIT_WORKFLOW.md](docs/GIT_WORKFLOW.md)。
