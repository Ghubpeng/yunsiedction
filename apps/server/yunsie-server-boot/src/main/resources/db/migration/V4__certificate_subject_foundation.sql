-- ============================================================================
-- V4: certificate + subject 知识体系基础（Stage 1.2）
-- 设计依据（已确认产品决策 + docs/CONFLICTS.md #2/#12）：
--   * 官方知识体系严格五级：证书 → 考试科目 → 章节 → 知识点 → 子知识点
--   * 课程 != 考试科目（course 是商业实体，后续 course 域实现）
--   * 通用 knowledge_node 模型（node_type 区分章节/知识点/子知识点），不建重复表
--   * 知识体系版本化：certificate → subject_version → knowledge_node
--   * 无限级树：parent_id + path(物化路径) + level + sort + enabled
-- 规范：database-design（域前缀/必备字段/无物理外键/唯一索引含 deleted/TINYINT 枚举注释）
-- 不实现：GraphRAG、知识图谱、掌握度（后续 Stage）
-- ============================================================================

-- ---------------------------- certificate 域 ----------------------------

CREATE TABLE certificate_category (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id   BIGINT UNSIGNED NOT NULL DEFAULT 0    COMMENT '父分类ID(0=根)',
    name        VARCHAR(100)    NOT NULL              COMMENT '分类名称',
    code        VARCHAR(50)     NOT NULL              COMMENT '分类编码',
    path        VARCHAR(500)    NOT NULL DEFAULT ''   COMMENT '物化路径, 如 /1/2/',
    level       INT             NOT NULL DEFAULT 1    COMMENT '层级(根=1)',
    sort        INT             NOT NULL DEFAULT 0    COMMENT '排序(越小越靠前)',
    enabled     TINYINT         NOT NULL DEFAULT 1    COMMENT '启用: 1-是 0-否',
    description VARCHAR(500)    NOT NULL DEFAULT ''   COMMENT '描述',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT         NOT NULL DEFAULT 0    COMMENT '逻辑删除: 0-否 1-是',
    version     INT             NOT NULL DEFAULT 0    COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_certificate_category_code (code, deleted),
    KEY idx_certificate_category_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '证书分类表(无限级树; 平台化, 不写死任何证书)';

CREATE TABLE certificate_cert (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_id BIGINT UNSIGNED NOT NULL COMMENT '所属分类ID',
    name        VARCHAR(100)    NOT NULL COMMENT '证书名称',
    code        VARCHAR(50)     NOT NULL COMMENT '证书编码',
    short_name  VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '简称',
    description VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '描述',
    enabled     TINYINT         NOT NULL DEFAULT 1 COMMENT '启用: 1-是 0-否',
    sort        INT             NOT NULL DEFAULT 0 COMMENT '排序(越小越靠前)',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version     INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_certificate_cert_code (code, deleted),
    KEY idx_certificate_cert_category (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '证书表(具体考试证书; 当前知识版本见 subject_version.status=2, 本表不冗余)';

-- ---------------------------- subject 域 ----------------------------

CREATE TABLE subject_subject (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    certificate_id BIGINT UNSIGNED NOT NULL COMMENT '所属证书ID',
    name           VARCHAR(100)    NOT NULL COMMENT '考试科目名称',
    code           VARCHAR(50)     NOT NULL COMMENT '科目编码',
    sort           INT             NOT NULL DEFAULT 0 COMMENT '排序(越小越靠前)',
    enabled        TINYINT         NOT NULL DEFAULT 1 COMMENT '启用: 1-是 0-否',
    source         TINYINT         NOT NULL DEFAULT 1 COMMENT '来源: 1-官方大纲 2-平台自建',
    description    VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '描述',
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_subject_subject (certificate_id, code, deleted),
    KEY idx_subject_subject_cert (certificate_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考试科目表(考试科目!=课程; 课程为商业实体, 见 course 域)';

CREATE TABLE subject_version (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    certificate_id BIGINT UNSIGNED NOT NULL COMMENT '所属证书ID',
    version_no     VARCHAR(30)     NOT NULL COMMENT '版本号, 如 2026',
    name           VARCHAR(100)    NOT NULL COMMENT '版本名称',
    status         TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1-草稿 2-当前 3-归档',
    enabled        TINYINT         NOT NULL DEFAULT 1 COMMENT '启用: 1-是 0-否',
    remark         VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '备注',
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_subject_version (certificate_id, version_no, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识体系版本表(大纲版本化; 每证书至多一个当前版本, 应用层保证)';

CREATE TABLE subject_knowledge_node (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    version_id     BIGINT UNSIGNED NOT NULL COMMENT '所属版本ID',
    certificate_id BIGINT UNSIGNED NOT NULL COMMENT '所属证书ID(冗余, 便于范围过滤)',
    subject_id     BIGINT UNSIGNED NOT NULL COMMENT '所属考试科目ID',
    parent_id      BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父节点ID(0=根)',
    node_type      TINYINT         NOT NULL COMMENT '节点类型: 1-章节 2-知识点 3-子知识点',
    name           VARCHAR(200)    NOT NULL COMMENT '节点名称',
    code           VARCHAR(100)    NOT NULL COMMENT '节点编码',
    path           VARCHAR(1000)   NOT NULL DEFAULT '' COMMENT '物化路径, 如 /1/2/3/',
    level          INT             NOT NULL DEFAULT 1 COMMENT '层级(章节=1, 知识点=2, 子知识点=3)',
    sort           INT             NOT NULL DEFAULT 0 COMMENT '排序(越小越靠前)',
    enabled        TINYINT         NOT NULL DEFAULT 1 COMMENT '启用: 1-是 0-否',
    source         TINYINT         NOT NULL DEFAULT 1 COMMENT '来源: 1-官方大纲 2-平台自建',
    remark         VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '备注',
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_subject_knowledge_node (version_id, code, deleted),
    KEY idx_subject_knowledge_node_subject (version_id, subject_id),
    KEY idx_subject_knowledge_node_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识节点表(通用模型: 章节/知识点/子知识点; 掌握度后续绑定本表+版本)';

-- ---------------------------- RBAC 权限点种子 ----------------------------

INSERT INTO sys_permission (parent_id, permission_code, permission_name, perm_type, path, icon, sort, status, remark) VALUES
(0, 'certificate:category:create', '证书分类新增', 3, '', '', 401, 1, '证书分类-新增'),
(0, 'certificate:category:update', '证书分类编辑', 3, '', '', 402, 1, '证书分类-编辑/移动'),
(0, 'certificate:category:delete', '证书分类删除', 3, '', '', 403, 1, '证书分类-删除'),
(0, 'certificate:category:read',   '证书分类查看', 3, '', '', 404, 1, '证书分类-查看/树'),
(0, 'certificate:manage',          '证书管理',     3, '', '', 411, 1, '证书-新增/编辑/删除'),
(0, 'certificate:read',            '证书查看',     3, '', '', 412, 1, '证书-列表/详情'),
(0, 'subject:create',              '知识体系新增', 3, '', '', 501, 1, '科目/版本/知识节点-新增'),
(0, 'subject:update',              '知识体系编辑', 3, '', '', 502, 1, '科目/知识节点-编辑/移动'),
(0, 'subject:delete',              '知识体系删除', 3, '', '', 503, 1, '科目/版本/知识节点-删除'),
(0, 'subject:read',                '知识体系查看', 3, '', '', 504, 1, '科目/版本/节点-查看/树'),
(0, 'subject:manage',              '知识体系版本管理', 3, '', '', 505, 1, '版本状态管理(设为当前/归档)');
