-- ============================================================================
-- V2: sys 域 RBAC 基础模型 + user 域账号/资料/登录凭证
-- 规范遵循 skills/database-design：
--   域前缀 snake_case；必备字段(id/create_time/update_time/deleted/version)；
--   TINYINT + 注释枚举（禁用 MySQL ENUM）；无物理外键；唯一索引含 deleted；
--   密码只存 BCrypt 哈希，严禁明文（skills/backend-development 安全条款）。
-- 迁移脚本不可变：已执行脚本禁止修改，错误用新脚本修正。
-- ============================================================================

-- ---------------------------- sys 域：RBAC ----------------------------

CREATE TABLE sys_role (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_code   VARCHAR(50)     NOT NULL                COMMENT '角色编码',
    role_name   VARCHAR(50)     NOT NULL                COMMENT '角色名称',
    role_type   TINYINT         NOT NULL DEFAULT 2      COMMENT '角色类型: 1-系统内置 2-自定义',
    status      TINYINT         NOT NULL DEFAULT 1      COMMENT '状态: 1-启用 0-禁用',
    remark      VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '备注',
    sort        INT             NOT NULL DEFAULT 0      COMMENT '排序(越小越靠前)',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0-否 1-是',
    version     INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (role_code, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色表(RBAC)';

CREATE TABLE sys_permission (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id       BIGINT UNSIGNED NOT NULL DEFAULT 0    COMMENT '父级ID(0=根)',
    permission_code VARCHAR(100)    NOT NULL              COMMENT '权限点编码: 域:资源:动作, 如 sys:role:create',
    permission_name VARCHAR(100)    NOT NULL              COMMENT '权限名称',
    perm_type       TINYINT         NOT NULL DEFAULT 3    COMMENT '类型: 1-目录 2-菜单 3-按钮/操作',
    path            VARCHAR(200)    NOT NULL DEFAULT ''   COMMENT '前端路由(菜单用)',
    icon            VARCHAR(100)    NOT NULL DEFAULT ''   COMMENT '图标',
    sort            INT             NOT NULL DEFAULT 0    COMMENT '排序(越小越靠前)',
    status          TINYINT         NOT NULL DEFAULT 1    COMMENT '状态: 1-启用 0-禁用',
    remark          VARCHAR(255)    NOT NULL DEFAULT ''   COMMENT '备注',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0    COMMENT '逻辑删除: 0-否 1-是',
    version         INT             NOT NULL DEFAULT 0    COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (permission_code, deleted),
    KEY idx_sys_permission_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '权限表(RBAC: 目录/菜单/按钮)';

CREATE TABLE sys_role_permission (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_id       BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    permission_id BIGINT UNSIGNED NOT NULL COMMENT '权限ID',
    create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version       INT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_permission (role_id, permission_id, deleted),
    KEY idx_sys_role_permission_permission (permission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色-权限关联表(RBAC)';

CREATE TABLE sys_data_scope (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_id       BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    scope_type    TINYINT  NOT NULL DEFAULT 2 COMMENT '范围类型: 1-全部数据 2-指定范围',
    resource_type VARCHAR(50) NOT NULL DEFAULT '*' COMMENT '资源类型: *-全部; course-课程; certificate-证书等(按业务扩展)',
    resource_id   BIGINT UNSIGNED NULL COMMENT '资源ID; NULL=该资源类型全部',
    create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version       INT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_sys_data_scope_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色数据权限范围表(RBAC DataScope)';

CREATE TABLE sys_user_role (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '用户ID(user域, 无物理外键)',
    role_id     BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version     INT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_role (user_id, role_id, deleted),
    KEY idx_sys_user_role_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户-角色关联表(RBAC; 表归sys域, user域经api契约操作)';

-- ---------------------------- user 域：账号/资料/凭证 ----------------------------

CREATE TABLE user_account (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username        VARCHAR(50)  NOT NULL COMMENT '登录账号',
    mobile          VARCHAR(20)  NULL COMMENT '手机号(展示脱敏; 加密存储后续加固)',
    nickname        VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '昵称',
    avatar          VARCHAR(255) NOT NULL DEFAULT '' COMMENT '头像URL',
    user_type       TINYINT      NOT NULL DEFAULT 1 COMMENT '用户类型: 1-学员 2-教师',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1-正常 0-禁用 2-锁定',
    last_login_time DATETIME     NULL COMMENT '最近登录时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_username (username, deleted),
    UNIQUE KEY uk_user_account_mobile (mobile, deleted),
    KEY idx_user_account_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户账号表';

CREATE TABLE user_profile (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    real_name   VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '真实姓名',
    gender      TINYINT      NOT NULL DEFAULT 0 COMMENT '性别: 0-未知 1-男 2-女',
    birthday    DATE         NULL COMMENT '生日',
    email       VARCHAR(100) NOT NULL DEFAULT '' COMMENT '邮箱',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version     INT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_profile_user (user_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户基础资料表';

CREATE TABLE user_credential (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    credential_type TINYINT  NOT NULL DEFAULT 1 COMMENT '凭证类型: 1-密码(预留扩展: 短信验证码等)',
    secret          VARCHAR(100) NOT NULL COMMENT '凭证密文(密码=BCrypt哈希; 严禁明文)',
    status          TINYINT  NOT NULL DEFAULT 1 COMMENT '状态: 1-正常 0-禁用',
    remark          VARCHAR(255) NOT NULL DEFAULT '' COMMENT '备注',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version         INT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_credential (user_id, credential_type, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户登录凭证表(密码仅存哈希)';

-- ---------------------------- 种子数据：超级管理员 ----------------------------

INSERT INTO sys_permission (parent_id, permission_code, permission_name, perm_type, path, icon, sort, status, remark)
VALUES (0, '*:*:*', '全部权限', 3, '', '', 0, 1, '系统内置通配权限(超级管理员)');

INSERT INTO sys_role (role_code, role_name, role_type, status, remark, sort)
VALUES ('super_admin', '超级管理员', 1, 1, '系统内置角色, 拥有全部权限', 0);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'super_admin' AND p.permission_code = '*:*:*';
