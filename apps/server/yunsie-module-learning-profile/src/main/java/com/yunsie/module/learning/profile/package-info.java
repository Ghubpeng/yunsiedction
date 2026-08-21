/**
 * learning-profile 域（学习档案）：学习历史汇总、知识点掌握度、薄弱点、学习日历、考试通过率预测。
 *
 * <p>消费式叶子域（CONFLICTS #17）：只经其他域 {@code api} 包读取 + 消费 ExamFinishedEvent，
 * 维护自有 {@code learn_*} 派生表；其他域不得依赖本域。</p>
 *
 * <p>采集机制（CONFLICTS #18）：进程内 Spring Event + @Async + learn_event_dedup 幂等 + 重算兜底；
 * 项目禁 MQ，不引入 Redis/消息中间件。</p>
 */
package com.yunsie.module.learning.profile;
