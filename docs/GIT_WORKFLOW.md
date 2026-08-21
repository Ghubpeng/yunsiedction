# Git 协作规范

## 分支模型

| 分支 | 用途 |
|---|---|
| `main` | 稳定版本（仅接受来自 develop 的合并；每次合并打版本标签） |
| `develop` | 日常开发集成分支 |
| `feature/*` | 功能开发分支（命名：`feature/stage-x.y-简短描述`） |
| `hotfix/*` | 线上缺陷修复（基于 main 切出，修复后同时合入 main 与 develop） |

## 协作流程

```
feature/* ──Pull Request──▶ develop ──合并+打标签──▶ main
```

1. 从 `develop` 切出 `feature/*` 分支开发。
2. 开发完成、本地全量验证通过（后端测试 + typecheck/build + dev E2E）后提交并推送。
3. 发起 Pull Request（feature → develop），至少 1 人评审；CI（如有）全绿后合并。
4. 版本发布：develop → main 合并后打标签（如 `v0.1.0`），标注本次发布内容。

## 提交规范

- 提交信息格式：`<type>: <subject>`（type ∈ feat / fix / refactor / docs / test / chore / style）。
- 示例：`feat: 新增证书体系复制`、`fix: 章节练习得分四舍五入错误`、`docs: 补充部署文档`。
- 一次提交只做一件事；禁止提交调试代码与临时文件。

## 铁律

- **禁止提交**：`.env`（`deploy/docker/.env` 等，参考 `.env.example` 自行维护）、密钥/token、数据库备份、node_modules、target、dist、test-results、playwright-report、IDE 配置（.idea/.vscode）。
- **skills/ 为唯一权威**：修改 skills 必须经明确授权，不得擅自改动。
- **数据库结构变更**：只允许通过 Flyway 追加式迁移（`apps/server/yunsie-server-boot/src/main/resources/db/migration/V*.sql`），禁止修改已应用的历史迁移文件。
- **合并前验证**：后端测试全绿、前端 typecheck/build 通过、dev E2E 全绿（如涉及用户端）。
