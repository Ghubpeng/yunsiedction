# 项目级 Skill 体系（AI 编程团队开发规范）

本目录是整个项目给 AI Agent（Claude Code / Cursor / 团队协作 Agent）使用的**规则库**。
原则：**先加载规则，再写代码**。任何功能开发开始前，由 `master-orchestrator` 判断涉及哪些 Skill，加载后再动手。

## 使用入口

- 任何需求先走 `master-orchestrator`（总管）：它负责识别涉及的 Skill、检查架构/数据库/权限/安全、排执行顺序。
- 每个 `SKILL.md` 的 `description`（frontmatter）定义了触发条件，Agent 会按需自动加载。
- 各 Skill 冲突时，优先级：`project-architecture` > 领域 Skill > 通用开发规范。

## Skill 清单

### 第一优先级（必须有）

| Skill | 用途 | 优先级 |
|---|---|---|
| `project-architecture` | 整个项目架构规则（技术栈锁定、分层、业务规则配置化） | ⭐⭐⭐⭐⭐ |
| `product-prd` | 按 PRD 开发，不乱加功能 | ⭐⭐⭐⭐⭐ |
| `frontend-development` | 用户端前端开发规范（React/多端） | ⭐⭐⭐⭐⭐ |
| `admin-development` | SmartAdmin 风格后台开发规范 | ⭐⭐⭐⭐⭐ |
| `backend-development` | Java/Spring Boot 后端规范 | ⭐⭐⭐⭐⭐ |
| `database-design` | MySQL 数据库设计规范 | ⭐⭐⭐⭐⭐ |
| `api-design` | 前后端 API 规范 | ⭐⭐⭐⭐⭐ |
| `ai-tutor` | AI 学习助手核心规范（场景感知，平台壁垒） | ⭐⭐⭐⭐⭐ |
| `rag-knowledge-base` | 课程/题库知识库（RAG 唯一知识来源） | ⭐⭐⭐⭐⭐ |
| `permission-rbac` | 管理员/教师/学员权限系统 | ⭐⭐⭐⭐⭐ |

### 第二优先级

| Skill | 用途 |
|---|---|
| `exam-engine` | 模拟考试 / 随机组卷 / 计时 / 判分 |
| `question-bank` | 题库系统（录入、审核、版本、快照） |
| `learning-profile` | 学习档案 / 知识掌握度 / 个性化数据基础 |
| `token-billing` | AI Token 计费（全链路、幂等、事务） |
| `payment-wallet` | 平台币 / VIP / 订单 / 支付回调 |

### 补充 Skill

| Skill | 用途 |
|---|---|
| `frontend-design-system` | 视觉语言 = React Bits 动效 + Cosmin 式质感 + 教育 SaaS 可用性 |
| `master-orchestrator` | 总管：需求 → 识别 Skill → 按序执行 → 检查 → 生成 → 测试 |
| `testing` | 测试规范（计费/权限/支付/组卷等核心链路必测） |

## 五个“灵魂” Skill

1. **`project-architecture`** — 任何代码服从现有架构；业务规则配置化，禁止写死。
2. **`ai-tutor`** — 场景感知：AI 知道当前用户/证书/课程/章节/知识点/题目/错题/档案/余额。这是与通用 AI Chat 的本质区别。
3. **`rag-knowledge-base`** — AI 回答优先基于平台知识库（官方教材/字幕/讲义/题库/大纲），并记录引用来源（“本回答依据：《基础护理学》第三章”）。
4. **`learning-profile`** — 学习历史/掌握度/薄弱点/强项/行为/考试预测，支撑个性化学习。
5. **`token-billing`** — 任何 AI 功能不得绕过 Token 计费；防重复扣费、并发扣费、失败扣费、负余额。

## 参考项目（只借鉴思想，不照搬）

- [React Bits](https://github.com/DavidHDev/react-bits)：React 动效组件库，作为 `frontend-design-system` 的视觉组件素材库（复制进项目改造，不整包引入）。
- [DeepTutor](https://github.com/HKUDS/DeepTutor)：借鉴其产品思想 —— 统一学习工作区、版本化 Knowledge Base、RAG/GraphRAG、Question Bank、Memory、Mastery Path、Skills。本平台面向**考试培训**重新设计，不直接照搬。

## 集成提示（按你的 Agent 工具选择）

- 本目录 `skills/<name>/SKILL.md` 是**唯一事实源**。
- Claude Code：可把本目录软链/复制到 `.claude/skills/` 下（PowerShell 示例）：
  ```powershell
  New-Item -ItemType Junction -Path .claude\skills -Target skills
  ```
- Cursor：在 `.cursor/rules/` 中按需要引用或转换为规则文件；保证与本目录内容一致，避免双源漂移。
- 修改规则必须走评审（review），禁止 Agent 在开发过程中顺手改 Skill 给自己“放宽规则”。

## 待你确认的架构决策点（确认后更新 `project-architecture`）

| # | 决策点 | 当前默认（可改） |
|---|---|---|
| 1 | 多端框架（H5/微信/抖音小程序） | Taro（React），与 Web 共享设计 Token 与业务逻辑 |
| 2 | 后端 ORM | MyBatis-Plus |
| 3 | 权限框架 | Spring Security + RBAC 自定义 |
| 4 | 向量库 | 待定（Milvus / pgvector / 云向量库） |
| 5 | 消息队列 | 待定（RocketMQ / RabbitMQ / Redis Stream） |
| 6 | 配置中心 | 待定（Nacos / Apollo / 环境变量起步） |
| 7 | Web 基础 UI 组件库 | Ant Design（React Bits 只做动效增强层） |
| 8 | 支付渠道 | 微信支付 / 支付宝（回调幂等见 `payment-wallet`） |
| 9 | 主观题阅卷 | 初期人工阅卷，后续引入 AI 辅助（需人工复核） |
| 10 | LLM 供应商 | 可配置多模型（单价表见 `token-billing`） |
