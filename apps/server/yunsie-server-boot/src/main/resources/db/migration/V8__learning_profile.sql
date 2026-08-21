-- ============================================================================
-- V8: learning-profile 域（Stage 1.6 学习档案）
-- 设计依据（Stage 1.6 已确认决策 + CONFLICTS #17~#20）：
--   * 独立模块 yunsie-module-learning-profile（消费式叶子域），避免 user<->course Maven 环（#17）
--   * 采集机制：Spring Event + @Async + 幂等去重 + 重算兜底（项目禁 MQ，崩溃窗口风险见 #18）
--   * 掌握度参数配置化：learn_config 域内自持（sys 无配置体系，见 #19）
--   * 掌握度按 user x node x version_id 隔离，知识版本切换后重新累积（#20）
--   * 表为派生投影：重算=物理删除本域行后重建（不改其他域数据）
-- 规范：database-design（域前缀/必备字段/无物理外键/唯一索引含 deleted/TINYINT 枚举注释）
-- ============================================================================

-- 学习历史汇总（一人一行；事件增量/重算全量重建）
CREATE TABLE learn_profile_summary (
    id                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id                   BIGINT UNSIGNED NOT NULL COMMENT '用户ID(user域)',
    total_study_seconds       BIGINT          NOT NULL DEFAULT 0 COMMENT '总学习秒数(练习耗时+考试时长+课程进度秒数近似)',
    course_finished_lessons   INT             NOT NULL DEFAULT 0 COMMENT '已完成课程小节数',
    practice_count            INT             NOT NULL DEFAULT 0 COMMENT '练习总数',
    practice_correct_count    INT             NOT NULL DEFAULT 0 COMMENT '练习正确总数',
    exam_count                INT             NOT NULL DEFAULT 0 COMMENT '考试次数(已交卷)',
    exam_best_score           DECIMAL(8,2)    NULL COMMENT '考试最佳成绩(无考试为 NULL)',
    streak_days               INT             NOT NULL DEFAULT 0 COMMENT '连续学习天数(截至今天/昨天)',
    last_study_date           DATE            NULL COMMENT '最近学习日期',
    create_time               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                   TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version                   INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learn_profile_summary (user_id, deleted),
    KEY idx_learn_profile_summary_streak (streak_days)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学习历史汇总表(派生投影; 重算时物理删除重建)';

-- 知识点掌握度（user x node x version_id；范围 0~100）
CREATE TABLE learn_mastery (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id             BIGINT UNSIGNED NOT NULL COMMENT '用户ID(user域)',
    certificate_id      BIGINT UNSIGNED NOT NULL COMMENT '证书ID(certificate域, 冗余自节点)',
    subject_id          BIGINT UNSIGNED NOT NULL COMMENT '考试科目ID(subject域, 冗余自节点)',
    node_id             BIGINT UNSIGNED NOT NULL COMMENT '知识节点ID(subject域)',
    version_id          BIGINT UNSIGNED NOT NULL COMMENT '知识体系版本ID(subject_version; 版本隔离依据)',
    mastery_value       INT             NOT NULL DEFAULT 0 COMMENT '掌握度 0~100',
    correct_count       INT             NOT NULL DEFAULT 0 COMMENT '该节点累计答对次数',
    wrong_count         INT             NOT NULL DEFAULT 0 COMMENT '该节点累计答错次数',
    last_practice_at    DATETIME        NULL COMMENT '最近练习时间(时间衰减依据)',
    last_trigger_source VARCHAR(20)     NOT NULL DEFAULT 'PRACTICE' COMMENT '最近更新来源: PRACTICE-练习 RECALC-重算',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learn_mastery (user_id, node_id, version_id, deleted),
    KEY idx_learn_mastery_user_cert (user_id, certificate_id),
    KEY idx_learn_mastery_user_value (user_id, mastery_value)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识点掌握度表(派生投影; 版本隔离: 不同 version_id 不混算)';

-- 学习日历（user x study_date 聚合；连续学习天数依据）
CREATE TABLE learn_study_calendar (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT UNSIGNED NOT NULL COMMENT '用户ID(user域)',
    study_date     DATE            NOT NULL COMMENT '学习日期',
    study_seconds  BIGINT          NOT NULL DEFAULT 0 COMMENT '当日学习秒数(练习耗时+考试时长; 课程仅记学习日)',
    practice_count INT             NOT NULL DEFAULT 0 COMMENT '当日练习次数',
    exam_count     INT             NOT NULL DEFAULT 0 COMMENT '当日考试次数(已交卷)',
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learn_study_calendar (user_id, study_date, deleted),
    KEY idx_learn_study_calendar_user_date (user_id, study_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学习日历表(派生投影; 课程学习历史无逐日事件, 课程仅标记学习日)';

-- 事件幂等去重表（append-only，不参与逻辑删除）
CREATE TABLE learn_event_dedup (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    dedup_key    VARCHAR(100)    NOT NULL COMMENT '事件去重键(如 exam:{attemptId})',
    event_type   VARCHAR(30)     NOT NULL COMMENT '事件类型: EXAM_FINISHED',
    processed_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '处理时间',
    create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learn_event_dedup (dedup_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学习事件幂等去重表(append-only; uk(dedup_key) 防重复累计)';

-- 学习档案配置（掌握度/预测参数配置化；learning-profile 域内自持）
CREATE TABLE learn_config (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key   VARCHAR(100)    NOT NULL COMMENT '配置键',
    config_value VARCHAR(255)    NOT NULL COMMENT '配置值',
    remark       VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '配置说明',
    create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version      INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learn_config (config_key, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学习档案配置表(掌握度权重/衰减/预测参数; 禁止硬编码魔法数字)';

-- ---------------------------- 配置种子 ----------------------------

INSERT INTO learn_config (config_key, config_value, remark) VALUES
('learn.mastery.correct.weight',        '12',   '答对基础加分(分)'),
('learn.mastery.wrong.weight',          '18',   '答错基础减分(分)'),
('learn.mastery.consolidation.factor',  '0.35', '多次答对巩固衰减系数(越大增益递减越快)'),
('learn.mastery.penalty.factor',        '0.25', '多次答错惩罚递增系数(越大惩罚递增越快)'),
('learn.mastery.decay.rate',            '0.05', '时间衰减率(每天, 超过宽限期后按 (1-rate)^天数)'),
('learn.mastery.decay.grace.days',      '7',    '时间衰减宽限期(天)'),
('learn.mastery.min',                   '0',    '掌握度下限'),
('learn.mastery.max',                   '100',  '掌握度上限'),
('learn.prediction.rule.version',       'v1.0', '预测规则版本(返回给前端, 可回溯)'),
('learn.prediction.history.weight',     '0.6',  '历史考试得分权重(剩余为掌握度权重)'),
('learn.prediction.recent.count',       '3',    '历史考试取样条数(最近N次)'),
('learn.prediction.min.samples',        '10',   '练习样本下限(低于则标注参考性弱)'),
('learn.prediction.default.pass.score', '60',   '及格线默认值(考试未配置 pass_score 时使用)'),
('learn.weakness.default.limit',        '10',   '薄弱点默认返回条数');

-- ---------------------------- RBAC 权限点种子 ----------------------------

INSERT INTO sys_permission (parent_id, permission_code, permission_name, perm_type, path, icon, sort, status, remark) VALUES
(0, 'learn:profile:read',   '档案查看(本人)', 3, '', '', 901, 1, '学习档案-本人查询'),
(0, 'learn:profile:view',   '学员档案查看',   3, '', '', 902, 1, '学习档案-教师/管理员查看学员(教师叠加课程 DataScope)'),
(0, 'learn:profile:recalc', '档案重算',       3, '', '', 903, 1, '学习档案-管理员重算兜底(幂等)');
