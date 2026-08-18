---
name: permission-rbac
description: 管理员/教师/学员权限系统规范（RBAC + 数据权限 + 权益判断统一入口）。开发任何涉及角色、菜单、按钮权限、数据范围、VIP 权益判断的功能时，必须先读本 Skill。
---

# permission-rbac（管理员/教师权限系统）

## 1. 定位

用户-角色-权限（菜单/按钮/数据）+ 权益（VIP 等）的统一权限体系。**服务端强制校验**，前端权限只用于 UI 显隐。

## 2. 模型

```
用户(user) ──多对多── 角色(role) ──多对多── 权限(permission)
   │
   └── 数据权限(DataScope)：按角色配置数据范围
权限 = 菜单权限 + 按钮/操作权限 + 数据权限
```

预设角色：超级管理员、运营、教师、学员；支持自定义角色（角色管理走 `sys` 域）。

## 3. 权限点定义（强制）

- 命名规范：`域:资源:动作`，如 `course:chapter:delete`、`exam:paper:publish`、`ai:usage:view`。
- 后端：注解/表达式校验（如 `@PreAuthorize("hasAuthority('course:chapter:delete')")`），统一收口，禁止散落 `if (roleId == 1)`。
- 前端：菜单/按钮由权限点渲染（`admin-development`），但**仅作 UI 控制**。
- 权限点随功能上线登记（集中清单，PRD 中列出新增权限点）。

## 4. 数据权限（强制，防越权核心）

- 教师：只能管理自己负责课程的章节/题目/试卷及学员数据。
- 学员：只能访问自己的学习档案、订单、流水、考试记录。
- 实现：统一 `DataScope` 机制（注解/拦截器向查询注入数据范围条件），**禁止在业务代码里散落 `if (userId == ...)` 判断**。
- 每条涉及他人资源的接口必须有横向越权用例：A 用户访问 B 资源 → 403（`testing`）。

## 5. 权益判断统一入口（与 project-architecture 铁律 2 联动）

- VIP 等权益：`vip_rights` 配置表（权益码 + 参数 + 生效条件）。
- 统一入口 `RightsService.hasRight(user, rightCode, context)`；业务代码只调用它。
- ❌ `if (vipLevel == 2) return free;`
- ✅ `rightsService.hasRight(user, "course.free_unlock", courseId)`
- 权益变更 = 改配置，不改代码、不发版。

## 6. 审计与安全

- 权限/角色变更、敏感操作（删除、审核、调价、退款）必须审计日志（操作人/时间/前后值）。
- 认证：JWT 短效 access + refresh 轮换；登录失败锁定与验证码策略。
- 默认拒绝：未显式授权一律拒绝；禁止“默认开放再慢慢收紧”。
- 后台与用户端权限体系同构，角色隔离；教师不得拥有运营/超管权限点。

## 7. 禁止事项（NEVER）

- 只做前端隐藏不做服务端校验。
- 业务代码写死角色/权益判断（`roleId==`、`vipLevel==`）。
- 数据范围判断散落在各 Service（必须走 DataScope）。
- 权限点随意增删不改 PRD 与清单。

## 8. 与其他 Skill 的关系

- 权益配置化铁律：`project-architecture`。
- 后台菜单/按钮接入：`admin-development`。
- 越权测试用例：`testing`。
- VIP/权益的商业规则：`payment-wallet`。

## 9. 检查清单

- [ ] 新增功能权限点已在 PRD 与清单登记？
- [ ] 后端强制校验 + 前端 UI 控制双到位？
- [ ] 涉及他人资源的接口有 DataScope？
- [ ] 权益判断全部走 RightsService，无硬编码？
- [ ] 横向越权用例已编写？
