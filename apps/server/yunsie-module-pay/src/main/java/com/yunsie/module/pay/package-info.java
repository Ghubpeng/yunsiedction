/**
 * pay 域：平台币钱包、订单、支付渠道、VIP。
 *
 * <p>已确认产品决策：
 * <ol>
 *   <li>平台币 = 平台主要商业支付媒介；用户可用平台币购买 Token（购买入账走 ai 域 Token 账户）。</li>
 *   <li>VIP MVP：购买 + 有效期 + 权益配置（vip_rights），不实现成长值体系；权益判断统一入口 RightsService，禁止写死。</li>
 *   <li>支付渠道：微信支付 + 支付宝（渠道抽象层，回调验签/幂等/金额校验/对账）。</li>
 * </ol></p>
 *
 * <p>资金铁律（token-billing / payment-wallet / backend-development）：
 * 幂等键 + 条件更新防并发/防负余额 + 流水不可变 + 操作审计 + 金额 DECIMAL。</p>
 *
 * <p>模块边界规则同 sys 域。</p>
 */
package com.yunsie.module.pay;
