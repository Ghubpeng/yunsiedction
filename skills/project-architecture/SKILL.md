---
name: project-architecture
description: 项目技术栈、模块划分、分层与全局铁律的唯一权威。开始任何新功能、引入任何新依赖、选择任何新技术前必须先读本 Skill；与其他 Skill 冲突时以本 Skill 为准。
---

# project-architecture（项目架构）

## 1. 定位

本 Skill 是全项目架构的**唯一权威**。任何代码都必须服从现有架构；禁止为了完成单个功能而另起技术体系。

## 2. 技术栈（锁定，不得随意变更）

| 层 | 技术 | 备注 |
|---|---|---|
| Frontend | React + TypeScript + Vite（Web）；H5 复用 Web 设计系统；微信/抖音小程序统一多端方案 | 多端框架默认 Taro（React）；确认后更新本表 |
| Backend | Java 17+ / Spring Boot 3.x / Maven | 主业务服务 |
| AI | Python / FastAPI | 独立 AI 服务，仅内网暴露 |
| Database | MySQL 8.x（InnoDB，utf8mb4） | 设计规范见 `database-design` |
| Cache | Redis | 缓存 / 分布式锁 / 幂等 |
| Storage | 对象存储（S3 兼容） | 视频、图片、文件 |
| AI Knowledge | 向量库 + RAG + 知识图谱 | 设计规范见 `rag-knowledge-base` |

## 3. 服务与分层

```
Frontend:  Web / H5 / 微信小程序 / 抖音小程序
                │ 统一 API（见 api-design）
                ▼
Backend:   Spring Boot（主业务：用户/课程/题库/考试/支付/权限）
                │ 内部 API（服务间鉴权，仅内网）
                ▼
AI:        Python FastAPI（AI Tutor / RAG / 知识处理）
                │
Database:  MySQL ── Redis ── 对象存储 ── 向量库
```

后端分层（自上而下，禁止跨层调用）：

```
Controller → Service（接口+实现）→ Mapper
     │           │
  DTO/VO      事务边界、业务规则
```

## 4. 模块划分（业务域）

| 域 | 职责 |
|---|---|
| `sys` | 系统、权限、字典、审计日志 |
| `user` | 学员、教师、学习档案 |
| `course` | 证书、课程、章节、知识点、视频 |
| `question` | 题库 |
| `exam` | 试卷、考试、成绩 |
| `ai` | AI 对话、计费流水、知识库管理 |
| `pay` | 钱包、订单、支付、VIP |

新增模块必须先在 PRD（`product-prd`）中立项并在本文件登记。

## 5. 铁律（MUST）

1. **不另起体系**：新增语言/框架/中间件/第三方库必须说明理由并先修改本 Skill，经评审后才能引入。
2. **业务规则配置化，禁止写死**：
   - ❌ `if (vipLevel == 2) return free;`
   - ✅ 权益规则存入配置表 → 统一权益判断服务（`RightsService`）→ 业务调用判断。
   - 目标：运营改规则（VIP 权益、优惠、限额）只改配置，不改代码、不重新发版。
   - 适用一切“可能被运营调整”的规则：价格、权益、限额、有效期、组卷配额、计费单价等。
3. **服务边界**：前端 ↔ 后端、后端 ↔ AI 服务之间只通过 API 通信；禁止跨服务直连数据库/Redis/对象存储。
4. **环境隔离**：dev / test / prod 配置外置（配置中心或环境变量），禁止硬编码密钥、地址、金额。
5. **全局统一**：统一响应体、错误码（`api-design`）、日志（traceId 贯穿）、操作审计。
6. **每张表、每个字段必须注释**；数据变更走迁移脚本（`database-design`）。

## 6. 禁止事项（NEVER）

- 在功能代码里复制一份架构（如另写一套鉴权、另一套 HTTP 封装、另一套计费）。
- 绕过主后端直接在前端调用 AI 服务或支付渠道。
- 为了“快”引入与现有体系重复的技术（新 ORM、新缓存、第二套前端框架）。
- 把业务规则（权益/价格/限额）硬编码进代码或前端。
- 未经评审修改本 Skill 或创建新的全局约定。

## 7. 与其他 Skill 的关系

- 所有 Skill 冲突时以本 Skill 为准。
- 数据模型细节见 `database-design`；接口细节见 `api-design`；开发纪律见 `backend-development` / `frontend-development`。
- 需求识别与执行顺序见 `master-orchestrator`。

## 8. 检查清单

- [ ] 是否引入了新依赖/新技术？→ 已在本 Skill 登记并评审
- [ ] 业务规则是否全部走配置？（无硬编码 if 权益判断）
- [ ] 是否遵守服务边界与分层？
- [ ] 新模块是否已立项并在模块划分中登记？
