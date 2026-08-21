-- ============================================================================
-- V5: question 域（Stage 1.3 题库 MVP）
-- 设计决策（Q1~Q7 已确认）：
--   * 题型：1-单选 2-多选 3-判断（TINYINT+注释枚举；判断题无选项）
--   * 答案：标准化答案串（A|C / T / F），选项独立表 question_option
--   * 状态机：1-草稿 2-待审核 3-驳回 4-已发布 5-已下架；发布=approve，不可绕过审核
--   * content_version=业务内容版本（发布后修改 +1 并回草稿重审）；BaseEntity.version=乐观锁，两者分离
--   * 题目-知识点关联：仅 知识点(2)/子知识点(3)，node 归 subject 域，本表只存 node_id（无物理外键）
--   * 错题本：uk(user_id, question_id, deleted) 幂等
--   * 练习记录=简单行为事件源（学习档案后续经事件聚合）
--   * 商业解锁（购买/VIP）推迟到 pay 阶段：本阶段登录用户即可练习
-- 规范：database-design（域前缀/必备字段/无物理外键/唯一索引含 deleted/TINYINT 枚举注释）
-- ============================================================================

CREATE TABLE question_question (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    certificate_id  BIGINT UNSIGNED NOT NULL COMMENT '所属证书ID',
    question_type   TINYINT         NOT NULL COMMENT '题型: 1-单选 2-多选 3-判断',
    stem            TEXT            NOT NULL COMMENT '题干',
    analysis        TEXT            NOT NULL COMMENT '解析(必填; 无解析不得发布, question-bank 铁律)',
    answer          VARCHAR(50)     NOT NULL COMMENT '标准答案(标准化答案串: A|C / T / F)',
    difficulty      TINYINT         NOT NULL DEFAULT 2 COMMENT '难度: 1-易 2-中 3-难',
    source          TINYINT         NOT NULL DEFAULT 2 COMMENT '来源: 1-真题 2-模拟题 3-自编',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1-草稿 2-待审核 3-驳回 4-已发布 5-已下架',
    content_version INT             NOT NULL DEFAULT 1 COMMENT '题目内容版本(业务版本; 发布后修改+1并回草稿重审; 与乐观锁version分离)',
    reject_reason   VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '驳回理由',
    audit_by        BIGINT UNSIGNED NULL COMMENT '审核人用户ID',
    audit_time      DATETIME        NULL COMMENT '审核时间',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_question_question_cert (certificate_id),
    KEY idx_question_question_status (status),
    KEY idx_question_question_cert_status (certificate_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '题目表(题库; 题干/解析 TEXT; 答案标准化串)';

CREATE TABLE question_option (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    question_id BIGINT UNSIGNED NOT NULL COMMENT '题目ID',
    option_key  CHAR(1)         NOT NULL COMMENT '选项键 A-Z',
    content     VARCHAR(500)    NOT NULL COMMENT '选项内容',
    sort        INT             NOT NULL DEFAULT 0 COMMENT '排序(越小越靠前)',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version     INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_question_option (question_id, option_key, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '题目选项表(判断题无选项)';

CREATE TABLE question_knowledge_node (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    question_id BIGINT UNSIGNED NOT NULL COMMENT '题目ID',
    node_id     BIGINT UNSIGNED NOT NULL COMMENT '知识节点ID(subject域; 仅知识点/子知识点)',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version     INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_question_knowledge_node (question_id, node_id, deleted),
    KEY idx_question_knowledge_node_node (node_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '题目-知识节点关联表(知识点/子知识点; 节点数据归 subject 域)';

CREATE TABLE question_mistake (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id            BIGINT UNSIGNED NOT NULL COMMENT '用户ID(user域)',
    question_id        BIGINT UNSIGNED NOT NULL COMMENT '题目ID',
    mistake_count      INT             NOT NULL DEFAULT 1 COMMENT '累计答错次数',
    last_mistake_time  DATETIME        NULL COMMENT '最近答错时间',
    last_practice_time DATETIME        NULL COMMENT '最近练习时间',
    status             TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1-未解决 2-已解决',
    create_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_question_mistake (user_id, question_id, deleted),
    KEY idx_question_mistake_user_status (user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '错题本表(用户+题目唯一, 重复答错累加不重复建行)';

CREATE TABLE question_practice_record (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id            BIGINT UNSIGNED NOT NULL COMMENT '用户ID(user域)',
    question_id        BIGINT UNSIGNED NOT NULL COMMENT '题目ID',
    practice_mode      TINYINT         NOT NULL COMMENT '练习模式: 1-顺序 2-分类 3-知识点 4-错题',
    submitted_answer   VARCHAR(50)     NOT NULL COMMENT '提交答案(标准化后)',
    standard_answer    VARCHAR(50)     NOT NULL COMMENT '标准答案快照(不可变)',
    correct            TINYINT         NOT NULL COMMENT '是否正确: 1-是 0-否',
    answer_time        DATETIME        NOT NULL COMMENT '作答时间',
    response_time_ms   INT             NULL COMMENT '响应耗时(毫秒)',
    knowledge_node_id  BIGINT UNSIGNED NULL COMMENT '知识点模式的目标节点ID(如有)',
    create_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_question_practice_user (user_id),
    KEY idx_question_practice_question (question_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '练习作答记录表(简单行为事件源; 学习档案后续聚合)';

-- ---------------------------- RBAC 权限点种子 ----------------------------

INSERT INTO sys_permission (parent_id, permission_code, permission_name, perm_type, path, icon, sort, status, remark) VALUES
(0, 'question:question:create', '题目新增', 3, '', '', 601, 1, '题库-新增/编辑入口(录入员)'),
(0, 'question:question:update', '题目编辑', 3, '', '', 602, 1, '题库-编辑/关联知识点'),
(0, 'question:question:delete', '题目删除', 3, '', '', 603, 1, '题库-删除'),
(0, 'question:question:read',   '题目查看', 3, '', '', 604, 1, '题库-列表/详情'),
(0, 'question:question:audit',  '题目审核', 3, '', '', 605, 1, '题库-审核(审核员, 与录入分离)'),
(0, 'question:question:import', '题目导入', 3, '', '', 606, 1, '题库-Excel 导入');
