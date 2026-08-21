/**
 * notify 域：通知与提醒。
 *
 * <p>MVP（CONFLICTS #21）：站内消息 —— ①成绩通知（消费 ExamFinishedEvent，@Async + uk 幂等）；
 * ②考试开考提醒（@Scheduled 扫描 valid_from 提前窗口，经 ExamQueryApi 定向该证书考试历史学员）。
 * 外部渠道（短信/微信推送）MVP 明确不实现，仅架构预留。</p>
 *
 * <p>模块边界：仅经他域 api 包交互（exam.api ExamFinishedEvent / ExamQueryApi）；禁止跨域直连表。</p>
 */
package com.yunsie.module.notify;
