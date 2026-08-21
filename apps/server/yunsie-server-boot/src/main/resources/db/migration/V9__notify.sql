-- ============================================================================
-- V9: notify 域（Stage 1.7 站内消息）
-- 设计依据（Stage 1.7 重估 + CONFLICTS #21）：
--   * MVP 仅站内消息；外部渠道（短信/邮件）仍为架构预留，不实现
--   * 消息来源1：成绩通知 —— 消费 exam 域 ExamFinishedEvent（异步 + uk 幂等）
--   * 消息来源2：考试开考提醒 —— @Scheduled 扫描 valid_from 进入提前窗口的已发布考试，
--     定向该证书有考试历史（exam_attempt）的学员；参数配置化（application.yml）
--   * 幂等去重：uk(user_id, message_type, biz_id, deleted) 天然去重（同一事件重复投递不重复发消息）
--   * 本人消息：读/已读/删除一律 @CurrentUser 严格本人数据
-- 规范：database-design（域前缀/必备字段/无物理外键/唯一索引含 deleted/TINYINT 枚举注释）
-- ============================================================================

CREATE TABLE notify_message (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT UNSIGNED NOT NULL COMMENT '接收用户ID(user域)',
    title        VARCHAR(100)    NOT NULL COMMENT '消息标题',
    content      VARCHAR(1000)   NOT NULL COMMENT '消息内容',
    message_type TINYINT         NOT NULL COMMENT '消息类型: 1-考试成绩通知 2-考试开考提醒 3-系统消息(预留)',
    biz_id       BIGINT UNSIGNED NOT NULL COMMENT '业务对象ID(成绩通知=attemptId; 开考提醒=examId; 幂等去重依据)',
    is_read      TINYINT         NOT NULL DEFAULT 0 COMMENT '已读: 0-未读 1-已读',
    read_time    DATETIME        NULL COMMENT '阅读时间',
    create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version      INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_notify_message (user_id, message_type, biz_id, deleted),
    KEY idx_notify_message_user_read (user_id, is_read, id),
    KEY idx_notify_message_user_time (user_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内消息表(本人数据; 事件驱动生成, uk 幂等去重)';
