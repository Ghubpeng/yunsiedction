-- ============================================================================
-- V6: exam 域（Stage 1.4 正式考试）
-- 设计依据（已确认方案 + exam-engine Skill）：
--   * 试卷快照不可变：exam_paper / exam_paper_question / exam_paper_option 生成后禁止修改与逻辑删除
--   * 历史考试完全脱离 question 当前数据（含答案/解析/分值/内容版本/知识点溯源）
--   * 计时唯一依据 = 服务端（attempt.expired_at = started_at + duration）
--   * 交卷幂等：条件更新 WHERE status=2；重复交卷不重复计分
--   * 成绩并入 exam_attempt（MVP 客观题即时判分，无主观题/阅卷）
--   * 规则组卷：assemble_rule JSON 配置化（总数/题型/知识点范围/难度），随机种子落库可复现
-- 规范：database-design（域前缀/必备字段/无物理外键/唯一索引含 deleted/TINYINT 枚举注释/金额 DECIMAL）
-- ============================================================================

CREATE TABLE exam_exam (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    certificate_id   BIGINT UNSIGNED NOT NULL COMMENT '所属证书ID',
    name             VARCHAR(100)    NOT NULL COMMENT '考试名称',
    duration_minutes INT             NOT NULL COMMENT '考试时长(分钟, 服务端计时依据)',
    total_score      DECIMAL(8,2)    NOT NULL DEFAULT 0 COMMENT '总分(组卷后回填)',
    pass_score       DECIMAL(8,2)    NULL COMMENT '及格线(可选; NULL=不判及格)',
    valid_from       DATETIME        NULL COMMENT '可参加开始时间(可选)',
    valid_until      DATETIME        NULL COMMENT '可参加截止时间(可选)',
    status           TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1-草稿 2-已发布 3-已下架',
    assemble_rule    VARCHAR(1000)   NOT NULL DEFAULT '' COMMENT '组卷规则JSON: questionCount/questionTypes/nodeIds/difficulty(配置化)',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version          INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_exam_exam_cert (certificate_id),
    KEY idx_exam_exam_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考试定义表(规则组卷; 发布后锁定)';

CREATE TABLE exam_paper (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    exam_id          BIGINT UNSIGNED NOT NULL COMMENT '所属考试ID',
    title            VARCHAR(100)    NOT NULL COMMENT '试卷标题(考试名快照)',
    duration_minutes INT             NOT NULL COMMENT '时长快照(分钟)',
    total_score      DECIMAL(8,2)    NOT NULL COMMENT '总分快照',
    question_count   INT             NOT NULL COMMENT '题目数量快照',
    status           TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1-有效 2-作废',
    assemble_seed    BIGINT          NOT NULL COMMENT '组卷随机种子(可复现, exam-engine 铁律)',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是(快照容器, 业务代码禁止置删)',
    version          INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_exam_paper_exam (exam_id),
    KEY idx_exam_paper_exam_status (exam_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '试卷快照容器表(不可变; 重新组卷=旧卷作废+新卷生效)';

CREATE TABLE exam_paper_question (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    paper_id        BIGINT UNSIGNED NOT NULL COMMENT '所属试卷ID',
    question_id     BIGINT UNSIGNED NOT NULL COMMENT '源题目ID(仅溯源, 无物理外键)',
    sort            INT             NOT NULL COMMENT '题号(从1开始)',
    score           DECIMAL(8,2)    NOT NULL DEFAULT 1 COMMENT '本题分值快照(MVP每题1分)',
    question_type   TINYINT         NOT NULL COMMENT '题型快照: 1-单选 2-多选 3-判断',
    stem            TEXT            NOT NULL COMMENT '题干快照',
    analysis        TEXT            NOT NULL COMMENT '解析快照',
    standard_answer VARCHAR(50)     NOT NULL COMMENT '标准答案快照(标准化答案串)',
    difficulty      TINYINT         NOT NULL DEFAULT 2 COMMENT '难度快照: 1-易 2-中 3-难',
    source          TINYINT         NOT NULL DEFAULT 2 COMMENT '来源快照: 1-真题 2-模拟题 3-自编',
    content_version INT             NOT NULL COMMENT '题目内容版本快照(溯源)',
    node_ids        VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '知识点关联快照(逗号分隔, 溯源)',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是(历史快照, 业务代码禁止置删)',
    version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exam_paper_question (paper_id, sort, deleted),
    KEY idx_exam_paper_question_paper (paper_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '试卷题目快照表(历史判分依据; 不可变, 禁止业务逻辑删除)';

CREATE TABLE exam_paper_option (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    paper_question_id BIGINT UNSIGNED NOT NULL COMMENT '试卷题目快照ID',
    option_key        CHAR(1)         NOT NULL COMMENT '选项键快照 A-Z',
    content           VARCHAR(500)    NOT NULL COMMENT '选项内容快照',
    sort              INT             NOT NULL DEFAULT 0 COMMENT '排序快照',
    create_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是(历史快照, 业务代码禁止置删)',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exam_paper_option (paper_question_id, option_key, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '试卷选项快照表(判断题无行; 不可变)';

CREATE TABLE exam_attempt (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    exam_id        BIGINT UNSIGNED NOT NULL COMMENT '所属考试ID',
    paper_id       BIGINT UNSIGNED NOT NULL COMMENT '绑定的试卷快照ID',
    user_id        BIGINT UNSIGNED NOT NULL COMMENT '用户ID(user域)',
    status         TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1-未开始 2-进行中 3-已交卷',
    started_at     DATETIME        NOT NULL COMMENT '开始时间(服务端时间)',
    expired_at     DATETIME        NOT NULL COMMENT '截止时间(服务端: started_at+duration; 计时唯一依据)',
    submitted_at   DATETIME        NULL COMMENT '交卷时间',
    score          DECIMAL(8,2)    NULL COMMENT '成绩(交卷判分后写入)',
    correct_count  INT             NULL COMMENT '答对题数',
    question_count INT             NOT NULL COMMENT '试卷题数快照',
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(并发交卷保护)',
    PRIMARY KEY (id),
    KEY idx_exam_attempt_exam_user (exam_id, user_id),
    KEY idx_exam_attempt_user_status (user_id, status),
    KEY idx_exam_attempt_timeout (status, expired_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考试实例表(一次参加; 成绩并入本表; 交卷条件更新幂等)';

CREATE TABLE exam_answer (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    attempt_id        BIGINT UNSIGNED NOT NULL COMMENT '考试实例ID',
    paper_question_id BIGINT UNSIGNED NOT NULL COMMENT '试卷题目快照ID',
    submitted_answer  VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '提交答案(暂存原文)',
    correct           TINYINT         NULL COMMENT '是否正确: 1-是 0-否(交卷判分后写入)',
    score             DECIMAL(8,2)    NULL COMMENT '本题得分(交卷判分后写入)',
    answered_at       DATETIME        NOT NULL COMMENT '最近作答时间',
    create_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exam_answer (attempt_id, paper_question_id, deleted),
    KEY idx_exam_answer_attempt (attempt_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考试作答明细表(暂存upsert幂等; 判分结果写入)';

-- ---------------------------- RBAC 权限点种子 ----------------------------

INSERT INTO sys_permission (parent_id, permission_code, permission_name, perm_type, path, icon, sort, status, remark) VALUES
(0, 'exam:exam:create',   '考试新增', 3, '', '', 701, 1, '考试-新增'),
(0, 'exam:exam:update',   '考试编辑', 3, '', '', 702, 1, '考试-编辑'),
(0, 'exam:exam:delete',   '考试删除', 3, '', '', 703, 1, '考试-删除(有考试记录禁止)'),
(0, 'exam:exam:read',     '考试查看', 3, '', '', 704, 1, '考试-列表/详情/试卷'),
(0, 'exam:exam:assemble', '考试组卷', 3, '', '', 705, 1, '考试-组卷/重新组卷'),
(0, 'exam:exam:publish',  '考试发布', 3, '', '', 706, 1, '考试-发布/下架'),
(0, 'exam:result:read',   '成绩查看', 3, '', '', 707, 1, '考试-管理端成绩列表');
