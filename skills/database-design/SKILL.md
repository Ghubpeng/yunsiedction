---
name: database-design
description: MySQL 数据库设计规范。设计/修改任何表结构、字段、索引、迁移脚本时使用；涉及金额、Token、状态枚举、必备字段时必须遵守本 Skill。
---

# database-design（MySQL 数据库设计规范）

## 1. 定位

MySQL 8.x / InnoDB / utf8mb4。所有表结构变更必须走迁移脚本，禁止手工改库。

## 2. 命名规范（强制）

| 对象 | 规则 | 示例 |
|---|---|---|
| 表名 | 业务域前缀 + snake_case；前缀：`sys_` `user_` `course_` `question_` `exam_` `ai_` `pay_` `learn_` | `course_chapter`、`pay_order`、`ai_token_ledger` |
| 字段 | snake_case 小写 | `knowledge_point_id` |
| 普通索引 | `idx_表名_字段` | `idx_course_chapter_course_id` |
| 唯一索引 | `uk_表名_字段` | `uk_pay_order_order_no` |
| 关联字段 | `xxx_id` | `chapter_id` |

每张表、每个字段必须有 `COMMENT`。

## 3. 必备字段（所有业务表）

```sql
id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
deleted        TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-否 1-是',
version        INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（并发更新场景必加）'
```

- 字典/配置类小表可省略 `version`；计费、钱包、库存、考试会话类必须加。
- 逻辑删除全局默认；审计类表（流水、日志）禁止逻辑删除（只追加，不修改）。

## 4. 字段类型规范（强制）

| 场景 | 类型 |
|---|---|
| 金额（元） | `DECIMAL(18,4)`；**禁止 float/double** |
| Token 数量 | `DECIMAL(18,6)` |
| 状态/枚举 | `TINYINT` + 注释枚举；**禁止 MySQL ENUM**；通用字典走字典表 |
| 时间 | `DATETIME`（不用 TIMESTAMP）；统一东八区约定 |
| 长文本（题干/解析/字幕） | `TEXT` / `MEDIUMTEXT`，仅内容类字段可用 |
| 主键 | `BIGINT UNSIGNED` 自增；对外暴露用加密 ID 或雪花 ID（见 `api-design`） |
| 布尔 | `TINYINT(1)` 0/1 |

## 5. 索引规范

1. 高频查询字段建索引；联合索引遵守最左前缀；`WHERE a=? AND b=? ORDER BY c` 场景按 (a, b, c) 设计。
2. 新索引必须用 `EXPLAIN` 验证；禁止无依据建索引、禁止冗余索引。
3. 唯一性用唯一索引保证（订单号、幂等键、渠道流水号），**不要只靠应用层判断**。
4. 大表结构变更走迁移脚本 + 低峰执行；禁止在线裸 `ALTER` 大表。

## 6. 约束（强制）

1. **禁止物理外键**；引用关系由应用层保证，但关联字段必须建索引。
2. 单表字段数超过 ~50 时考虑拆分（扩展表/JSON 列仅用于扩展属性）。
3. 禁止 `SELECT *`；禁止大字段参与高频查询。
4. 冗余字段（如订单冗余快照）必须有注释说明同步策略。

## 7. 特殊约定

- **快照**：试卷实例、订单、流水中的业务快照（题目内容、单价）一旦生成不可变（见 `exam-engine`、`token-billing`）。
- **配置化规则**：权益/价格/限额等运营规则存配置表（`sys_` 或域配置表），禁止散落在代码（`project-architecture` 铁律 2）。
- **敏感字段**：手机号等可加密存储；密码只存哈希。
- **迁移**：用 Flyway（或等价工具）版本化所有 DDL/DML；迁移脚本不可变，错误用新脚本修正。

## 8. 禁止事项（NEVER）

- 物理外键、MySQL ENUM、金额用 float/double。
- 手工改生产库；迁移脚本不评审直接执行。
- 无注释的表/字段；无依据的索引；`SELECT *`。
- 把运营规则常量写进表结构里“一表一规则”（规则应配置化）。

## 9. 检查清单

- [ ] 命名合规（域前缀 + snake_case）？表/字段有 COMMENT？
- [ ] 必备字段齐全？并发表有 version？
- [ ] 金额 DECIMAL、枚举 TINYINT、无 ENUM/物理外键？
- [ ] 索引有 EXPLAIN 依据？唯一性有唯一索引兜底？
- [ ] 变更走迁移脚本？
