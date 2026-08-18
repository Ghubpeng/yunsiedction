---
name: token-billing
description: AI Token 计费规范（全链路计量、预扣结算、幂等、防重复/并发扣费、防负余额、流水）。开发或修改任何 AI 调用链路、余额扣减、Token 流水、计费配置时，必须先读本 Skill。
---

# token-billing（AI Token 计费）

## 1. 定位

所有 AI 功能的**唯一计费入口**。任何 AI 功能不得绕过本链路。它是平台的“灵魂”之一：用户按实际 Token 消耗付费。

## 2. 计费链路（强制）

```
AI 请求
  ↓ ① 预检：余额是否 ≥ 预扣额（预估上限）；不足 → 直接拒绝，不发请求
  ↓ ② 预扣（流式场景）：按 max_tokens 上限预扣（冻结/扣减，见 §4）
  ↓ ③ 调用 AI 服务
  ↓ ④ 按模型实际返回 usage 结算：input token × 输入单价 + output token × 输出单价
  ↓ ⑤ 多退少补：预扣 > 实际 → 退还差额；预扣 < 实际 → 补扣（需余额充足）
  ↓ ⑥ 扣除用户余额（余额不足时结算前已由预扣保障）
  ↓ ⑦ 写流水（ai_token_ledger，不可变）
```

- 单价配置化：`ai_model_price`（model、input_price、output_price，单位：元/1K tokens），运营可调（`project-architecture` 铁律 2）。
- 计量**以 AI 服务返回的 usage 为准**；前端报的任何 token 数一律不采信。

## 3. 必须防住的五件事（每条有对应机制）

| 风险 | 机制 |
|---|---|
| 重复扣费 | 幂等键 `request_id` 唯一索引；同 request_id 重放直接返回首次结果 |
| 并发扣费 | 条件更新 `UPDATE wallet SET balance = balance - ? WHERE user_id = ? AND balance >= ?`，行数=0 即失败（`backend-development`） |
| AI 失败却扣费 | 调用失败/超时/异常 → 全额退款 + 告警；退款幂等 |
| 重试重复扣费 | 重试必须复用同一 request_id；结算以 request_id 为准只落一次 |
| 余额不足产生负数 | 条件更新兜底 + 预检预扣；**任何代码路径禁止产生负余额** |

## 4. 预扣与结算细则

- 流式（SSE）：先按预估上限预扣 → 输出结束按 usage 结算多退少补；断连/客户端取消：按已产生 usage 结算（服务端记录）。
- 非流式：调用完成后一次结算。
- 预扣与结算都必须写流水；退差价写 `type=refund` 流水；流水不可修改、不可删除（`database-design` 审计类表）。
- 幂等键同时用于：计费请求、AI 服务调用、退款（三层同键或映射，防交叉重复）。

## 5. 流水（强制）

```
ai_token_ledger：request_id、user_id、scene、model、
  input_tokens、output_tokens、cost(DECIMAL)、type(消费/退款/赠送)、
  balance_after、create_time
```

- 余额变化必须同时体现在账户流水（`payment-wallet`），两套流水对得上（对账）。
- 后台可查用户 Token 流水与余额；修改余额只能走充值/调账接口 + 审计（禁止手工改库）。

## 6. 规则配置化（强制）

- 单价、预扣比例、免费额度（如有）、赠送策略全部配置化。
- “某场景免计费”必须在配置中显式声明（`billing_rule` 表 + PRD），禁止代码写死 `if (free)`。

## 7. 监控与告警

- 模型实际成本 vs 售价（毛利监控）；异常流水（负成本、超大 usage、退款率过高）告警。
- 计费链路失败率、结算延迟监控。

## 8. 禁止事项（NEVER）

- 任何 AI 功能绕过计费网关（包括“先上线后补计费”）。
- 采信前端 token 数；手工改余额/流水。
- 负余额；同一次调用重复落流水。
- 单价/规则硬编码在代码或前端。
- 预扣与结算无流水、无对账。

## 9. 与其他 Skill 的关系

- 账户/余额/充值：`payment-wallet`；AI 调用链路：`ai-tutor`；事务并发：`backend-development`；数据模型：`database-design`（`ai_` 域）；测试：`testing`（计费必测清单）。

## 10. 检查清单

- [ ] 链路完整：预检 → 预扣 → 结算 → 流水？
- [ ] 五类风险各有机制兜底？
- [ ] request_id 幂等 + 唯一索引？
- [ ] 单价/规则配置化，无硬编码免费场景？
- [ ] 余额流水与 AI 流水可对账？
