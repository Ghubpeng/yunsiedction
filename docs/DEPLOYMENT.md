# 部署与交付基线（Stage 2.0）

本平台支持两种运行形态，互不破坏：

| 形态 | 用途 | 入口 |
|---|---|---|
| 本地开发 | 热更新调试 | `scripts\dev-infra.ps1` + Maven/Vite 本地启动（README 快速开始） |
| 容器交付 | 一键构建/启动/对外演示 | 本文档（`scripts\build.ps1` / `up.ps1` / `health.ps1` / `down.ps1`） |

## 快速启动（容器交付）

```powershell
# 1) 配置环境变量（首次；生产必须逐项覆盖，禁止提交 .env）
Copy-Item deploy\docker\.env.example deploy\docker\.env

# 2) 构建镜像（server + web，首次较慢）
powershell -ExecutionPolicy Bypass -File scripts\build.ps1

# 3) 启动完整应用栈（基础设施 + server + web；server 等待 MySQL/Redis/MinIO 健康后启动）
powershell -ExecutionPolicy Bypass -File scripts\up.ps1

# 4) 健康检查（80/5173 首页/actuator/mysql/redis/minio/反代链路 七项探活）
powershell -ExecutionPolicy Bypass -File scripts\health.ps1

# 5) 播种 Demo 演示数据（幂等；YUNSIE_DEMO_ENABLED=true 时）
powershell -ExecutionPolicy Bypass -File scripts\demo.ps1

# 6) 打开 Web
#    http://localhost/              演示体验入口（Stage 2.1：打开即是产品首页）
#    http://localhost:5173/         兼容旧端口（同一 nginx）
#    http://localhost:9001          MinIO 控制台（开发默认凭据见 .env）
```

Demo 体验链路：首页 →「进入 Demo」一键登录（真实 JWT，仅免除输入账号密码）→ 学习首页（当前课程/最近学习/学习进度/连续学习）→ 课程 → 视频播放 → 学习档案。
Demo 入口仅为演示构建开启：`YUNSIE_DEMO_ENABLED=true`（默认 false；前端按钮由构建参数 VITE_DEMO_ENABLED 注入，生产构建无该按钮，后端端点也不存在）。

停止：`scripts\down.ps1`（保留数据卷）；彻底清空数据（危险）：`docker compose -f deploy\docker\compose.yml down -v`。

## 环境变量表（deploy/docker/.env，仓库内仅 .env.example）

| 变量 | 用途 | 必填 | 默认值 | 示例 | secret |
|---|---|---|---|---|---|
| MYSQL_ROOT_PASSWORD | MySQL root 密码（server 连接用） | 生产必填 | devpassword | ChangeMe_2026! | 是 |
| MYSQL_DATABASE | 业务库名 | 否 | yunsie_platform | yunsie_platform | 否 |
| MYSQL_USER | server 连接用户 | 否 | root | app | 否 |
| REDIS_PORT | Redis 宿主端口 | 否 | 6379 | 6379 | 否 |
| MINIO_ROOT_USER | MinIO 访问账号 | 生产必填 | minioadmin | yunsie-minio | 是 |
| MINIO_ROOT_PASSWORD | MinIO 访问密钥 | 生产必填 | minioadmin | …… | 是 |
| MINIO_BUCKET | 视频对象桶 | 否 | yunsie-videos | yunsie-videos | 否 |
| MINIO_PUBLIC_ENDPOINT | 浏览器可达的 MinIO 地址（预签名播放 URL 的 Host；本机部署默认即可，远程部署必须改为对外地址） | 远程必填 | http://localhost:9000 | https://oss.example.com | 否 |
| MINIO_REGION | MinIO 区域（预签名显式区域，容器内预签名不发 region 发现请求） | 否 | us-east-1 | us-east-1 | 否 |
| YUNSIE_DEMO_ENABLED | Demo 一键登录端点 + 前端「进入 Demo」按钮（**生产必须 false**） | 否 | false | true（演示） | 否 |
| YUNSIE_DEMO_USERNAME | 演示学员账号（由 scripts/demo.ps1 播种） | 否 | demo_learner | demo_learner | 否 |
| YUNSIE_JWT_SECRET | JWT 签名密钥（≥32 字符随机串） | 生产必填 | dev-only 占位 | …… | 是 |
| SERVER_JAVA_OPTS | server JVM 参数 | 否 | -XX:MaxRAMPercentage=75.0 | -Xmx2g | 否 |
| AI_INTERNAL_API_SECRET | AI 服务间鉴权（后置） | 否 | 空 | —— | 是 |
| AI_PROVIDERS__DEEPSEEK__* | AI Provider（后置） | 否 | 空 | —— | 是 |

前端 API base：生产构建默认同源相对路径 `/api`（由 nginx 反代），无需配置；
如需独立域名部署，构建时设置 `VITE_API_BASE_URL`（见 apps/web/.env.example）。

## 服务说明

| 服务 | 镜像/构建 | 职责 | 端口（宿主:容器） | 依赖 | 持久化 |
|---|---|---|---|---|---|
| web | apps/web（node 构建 → nginx） | React 静态资源 + SPA fallback + `/api` 反代 server；80 为演示入口 | 80:80 / 5173:80 | server 健康 | 无（无状态） |
| server | apps/server（Maven 构建 → JRE，非 root） | 全部业务 API；Flyway 启动时迁移 V1~V9 | 8080:8080 | mysql/redis/minio 健康 | 无（数据在 MySQL/MinIO） |
| mysql | mysql:8.4 | 业务数据 | 3306:3306 | —— | mysql-data 卷 |
| redis | redis:7.4-alpine | 预留（当前业务零使用，不新增业务能力） | 6379:6379 | —— | redis-data 卷 |
| minio | minio/minio | 课程视频对象存储（yunsie-videos 桶） | 9000/9001 | —— | minio-data 卷 |
| ai-service | profile=ai 按需 | 后置（LLM/RAG） | 8000 | —— | —— |

## 运维

- **日志**：`docker compose -f deploy\docker\compose.yml logs -f server web`；单服务加服务名。
- **重启**：`docker compose -f deploy\docker\compose.yml restart server`（或整个栈 `up -d --force-recreate`）。
- **停止**：`scripts\down.ps1`（保留数据卷）。
- **健康检查**：`scripts\health.ps1`；容器内 healthcheck 状态：`docker inspect --format '{{.State.Health.Status}}' yunsie-server-app`。
- **数据卷**：`docker volume ls | Select-String yunsie`；备份 MySQL：`docker exec yunsie-mysql sh -c "mysqldump -uroot -p\$MYSQL_ROOT_PASSWORD yunsie_platform" > backup.sql`。
- **Flyway**：由 server 应用启动时自动执行（classpath db/migration V1~V9），无独立迁移容器；迁移失败会阻止应用健康，查日志定位。
- **MinIO**：控制台 http://localhost:9001；桶 `yunsie-videos` 由应用上传时自动创建；对象键 `course/{courseId}/{lessonId}/{uuid}.mp4`。
  server 容器内 SDK 走内网 `minio:9000`，预签名播放 URL 用 `MINIO_PUBLIC_ENDPOINT` 对外地址签名（S3 签名含 Host，两套端点必须分开）。
- **常见故障**：
  - web 502/502 链路 → server 未健康：`scripts\health.ps1` 定位（依赖未就绪/Flyway 失败/端口占用）。
  - 视频上传 413 → 检查 nginx `client_max_body_size 500m` 与后端 `spring.servlet.multipart`（均已配置为 500MB）。
  - 视频播放 404 → MinIO 桶/对象存在性：`docker exec yunsie-minio ls -R /data/yunsie-videos`。
  - 登录后刷新失效 → JWT secret 在 .env 与运行容器间不一致（重建容器生效）。

## 安全

- 仓库内仅 `deploy/docker/.env.example`（占位值）；`.env` 已被 .gitignore 忽略。
- Dockerfile/compose/nginx 中无任何真实密码、密钥（JWT 默认值仅开发占位，application.yml 同语义）。
- server 容器以非 root 用户运行；web 以 nginx 官方镜像默认用户运行。
- API 响应不缓存（nginx no-store）；`index.html` 不缓存；带 hash 静态资源长缓存。
