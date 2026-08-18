---
name: master-orchestrator
description: 项目总管。收到任何新需求、新功能或改动请求时，先加载本 Skill：判断涉及哪些 Skill、检查架构/数据库/权限/安全、排定执行顺序，再开始写代码。目标：防止“改一个功能，破坏三个功能”。
---

# master-orchestrator（总管）

## 1. 定位

本 Skill 是需求到代码的**总调度**。它不替代任何领域 Skill，而是决定“这个需求要过哪几道关”。

## 2. 标准流程

```
用户需求
   ↓
① 立项：生成/更新 PRD（product-prd，九段 + 影响面分析）
   ↓
② 识别涉及 Skill（见下方映射表）
   ↓
③ 按顺序加载并执行：
   架构检查(project-architecture) → 数据设计(database-design)
   → 接口设计(api-design) → 后端(backend-development)
   → 前端(frontend-development/admin-development/design-system)
   → 计费/权限/领域 Skill → 测试(testing)
   ↓
④ 生成代码（遵守各 Skill 强制规则）
   ↓
⑤ 回归检查：列出本次改动可能影响的功能清单并验证
```

## 3. Skill 映射表（功能类型 → 涉及 Skill）

| 需求类型 | 必加载 Skill（按执行顺序） |
|---|---|
| 常规业务功能 | product-prd → project-architecture → database-design → api-design → backend-development → frontend-development → testing |
| 后台管理功能 | 上述 + admin-development（替代/叠加 frontend-development） |
| 涉及权限/角色 | + permission-rbac |
| 涉及 AI 对话/答疑 | + ai-tutor + rag-knowledge-base + token-billing |
| 涉及考试/组卷 | + exam-engine + question-bank |
| 涉及学习档案/掌握度 | + learning-profile |
| 涉及付费/VIP/订单 | + payment-wallet + permission-rbac（权益判断） |
| 涉及视觉/动效/页面 | + frontend-design-system |
| 所有需求 | product-prd 前置，testing 收尾 |

## 4. 示例：需求“增加一个 VIP 免费课程功能”

按流程展开，不得直接写代码：

1. **product-prd**：立项（哪些课程适用、免费规则、有效期、是否与优惠叠加）。
2. **payment-wallet**：VIP 权益定义（配置项 `vip_rights`）。
3. **permission-rbac**：权益判断统一入口（RightsService），禁止 `if (vipLevel==2)`。
4. **database-design**：订单/购买记录对“免费”的记账方式（0 元订单还是权益直接解锁，PRD 定）。
5. **backend-development**：下单/解锁流程、事务边界、幂等。
6. **frontend-development**：课程列表免费标识、解锁流程页面。
7. **testing**：VIP 未到期/过期/非 VIP/权益叠加 4 类用例 + 回归清单。

## 5. 冲突解决规则

- 两个 Skill 冲突 → 以 `project-architecture` 为准。
- 计费口径冲突 → 以 `token-billing` 为准。
- 权限判断冲突 → 以 `permission-rbac` 的统一入口为准，禁止业务代码自造判断。
- 视觉/动效冲突 → 以 `frontend-design-system` 的克制原则为准。

## 6. 变更影响清单（防“改一个坏三个”）

任何改动在动手前必须列出：

1. 受影响的数据表与接口。
2. 受影响的角色（学员/教师/运营/超管）。
3. 是否触碰计费/钱包（→ 幂等、流水、对账回归）。
4. 是否触碰考试/题库快照（→ 历史数据回归）。
5. 是否有依赖它的其他功能（如权益判断、AI 上下文）。
6. 回归用例清单（至少覆盖清单中每一项）。

## 7. 禁止事项（NEVER）

- 跳过 PRD 直接写代码。
- 只加载一个领域 Skill 就开工（必须过架构/数据/权限检查）。
- 不做影响面分析就改动共享模块（权益、计费、题库、权限）。
- 在生成代码后才“补”架构与数据设计。

## 8. 检查清单

- [ ] PRD 已立项且含影响面分析？
- [ ] 已按映射表加载全部相关 Skill？
- [ ] 变更影响清单已列出并附回归用例？
- [ ] 生成代码前已通过架构/数据库/权限/安全检查？
