---
name: frontend-development
description: 用户端前端开发规范（Web/H5/小程序）。开发任何用户端页面、组件、状态管理、请求封装、路由时使用；视觉与动效约束见 frontend-design-system，后台页面见 admin-development。
---

# frontend-development（用户端前端开发规范）

## 1. 定位

用户端（Web / H5 / 微信小程序 / 抖音小程序）的前端工程规范。视觉规则见 `frontend-design-system`；本 Skill 只管工程与代码纪律。

## 2. 技术栈

- Web：React + TypeScript + Vite；路由 React Router；状态管理默认 Zustand（局部状态用 hooks，不引入 Redux 样板）。
- H5：复用 Web 设计系统与业务组件（响应式/独立入口）。
- 小程序：Taro（React）统一多端（决策点 #1，最终以 `project-architecture` 为准）；设计 Token 与业务逻辑层与 Web 共享。
- 基础 UI 组件库：默认 Ant Design（决策点 #7）；React Bits 只做动效增强层（`frontend-design-system`）。

## 3. 目录结构（约定）

```
src/
  pages/        页面（按业务域分目录）
  components/   通用业务组件
  hooks/        自定义 hooks
  api/          接口封装（按域拆分，禁止页面内裸写请求）
  stores/       全局状态（Zustand）
  utils/        工具函数
  styles/       Design Token（CSS 变量）
```

## 4. 强制规则（MUST）

1. TypeScript 严格模式（`strict: true`），禁止 `any`（确有需要须注释说明）。
2. 所有请求必须走统一封装（axios/fetch 拦截器）：注入 token、自动刷新、错误码转提示、traceId 透传、超时与重试策略；禁止页面内裸写请求。
3. 权限只做 UI 控制（按钮/菜单显隐），**服务端才是最终校验**（`permission-rbac`）。
4. 加载态、空态、错误态必须齐全（组件取材遵循 `frontend-design-system`）。
5. 每个页面必须有路由懒加载；长列表（题库、考试记录、流水）用虚拟滚动。
6. 所有文案/规则展示不得硬编码业务规则（价格、权益、限额从接口返回；规则定义在后端配置）。
7. 动效纪律：题目页、考试页、视频页、学习档案、表格类页面**不做装饰性动效**（见 `frontend-design-system`）。
8. 错误边界：页面级 ErrorBoundary，崩溃不得白屏。
9. ESLint + Prettier 统一格式化；组件命名 PascalCase，文件名与组件同名。
10. 兼容 `prefers-reduced-motion`，任何动效必须可降级为静态。

## 5. 状态管理约定

- 服务端数据：接口层 + 缓存（如 TanStack Query），禁止复制进全局 store。
- 全局 store 只放：登录态、主题、设备信息、极少量跨页共享状态。
- 页面局部状态用 hooks；表单用受控组件 + 校验库（规则与后端 Bean Validation 对齐）。

## 6. 性能要求

- 首屏懒加载、图片懒加载、视频预加载策略（学习场景优先级最高）。
- 列表分页加载；防抖/节流（搜索、AI 输入联想）。
- 构建产物按路由分包；小程序主包体积受控（决策点 #1 时明确分包策略）。

## 7. 禁止事项（NEVER）

- 绕过 `api/` 封装直接 fetch/axios。
- 前端写死业务规则（“VIP 免费”判断、价格、Token 单价）。
- 在考试/答题页面加动效（影响专注与稳定性）。
- 内联样式泛滥（用 Design Token，见 `frontend-design-system`）。
- 页面组件超过 ~300 行不拆分；一个组件干三件事。
- 前端自己拼接 AI prompt（只能传场景锚点，见 `ai-tutor`）。

## 8. 与其他 Skill 的关系

- 视觉/动效/组件取材：`frontend-design-system`。
- 接口契约：`api-design`。
- 权限组件用法：`permission-rbac`。
- AI 相关页面：`ai-tutor` 的上下文约定与计费提示（`token-billing`）。

## 9. 检查清单

- [ ] strict TS 无 any；ESLint/Prettier 通过？
- [ ] 请求走统一封装；无页面内裸写？
- [ ] 三态（加载/空/错误）齐全？
- [ ] 无前端写死的业务规则？
- [ ] 核心学习界面无装饰动效？
