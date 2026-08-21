-- ============================================================================
-- V3: 认证与权限闭环
--   1) user_refresh_token：刷新令牌表 —— 仅存 SHA-256 哈希（不存明文 token），
--      支持轮换与撤销；不使用 Redis 会话（决策见 docs/ARCHITECTURE.md / 报告）。
--   2) 权限点种子：sys:user:* / sys:role:* / sys:permission:*
--   3) 超级管理员数据范围（scope_type=1 全部数据）
--   4) 开发种子管理员账号 admin / Admin@123456（仅开发环境；生产必须禁用/改密，
--      密码为 BCrypt 哈希，绝不明文）
-- 规范：database-design（域前缀/必备字段/无物理外键/唯一索引含 deleted）。
-- ============================================================================

CREATE TABLE user_refresh_token (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    token_hash  CHAR(64)        NOT NULL COMMENT '刷新令牌SHA-256哈希(64位hex; 不保存明文token)',
    expires_at  DATETIME        NOT NULL COMMENT '过期时间',
    revoked     TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已撤销: 0-否 1-是',
    remark      VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version     INT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_refresh_token_hash (token_hash, deleted),
    KEY idx_user_refresh_token_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '刷新令牌表(仅存SHA-256哈希; 轮换+撤销, 不使用Redis)';

-- 用户类型语义扩展：增加 3-管理员（平台内部账号；权限仍由 RBAC 角色决定）
ALTER TABLE user_account
    MODIFY COLUMN user_type TINYINT NOT NULL DEFAULT 1 COMMENT '用户类型: 1-学员 2-教师 3-管理员';

-- ---------------------------- 权限点种子 ----------------------------
INSERT INTO sys_permission (parent_id, permission_code, permission_name, perm_type, path, icon, sort, status, remark) VALUES
(0, 'sys:user:view',       '用户查看',     3, '', '', 101, 1, '用户管理-查看/列表'),
(0, 'sys:user:create',     '用户新增',     3, '', '', 102, 1, '用户管理-新增'),
(0, 'sys:user:update',     '用户编辑',     3, '', '', 103, 1, '用户管理-编辑/状态'),
(0, 'sys:user:password',   '重置密码',     3, '', '', 104, 1, '用户管理-重置密码'),
(0, 'sys:user:roles',      '分配角色',     3, '', '', 105, 1, '用户管理-分配角色'),
(0, 'sys:user:delete',     '用户删除',     3, '', '', 106, 1, '用户管理-删除'),
(0, 'sys:role:view',       '角色查看',     3, '', '', 201, 1, '角色管理-查看/列表'),
(0, 'sys:role:create',     '角色新增',     3, '', '', 202, 1, '角色管理-新增'),
(0, 'sys:role:update',     '角色编辑',     3, '', '', 203, 1, '角色管理-编辑'),
(0, 'sys:role:delete',     '角色删除',     3, '', '', 204, 1, '角色管理-删除'),
(0, 'sys:role:assign',     '角色授权',     3, '', '', 205, 1, '角色管理-分配权限/数据范围'),
(0, 'sys:permission:view', '权限查看',     3, '', '', 301, 1, '权限管理-查看/树'),
(0, 'sys:permission:create','权限新增',    3, '', '', 302, 1, '权限管理-新增'),
(0, 'sys:permission:update','权限编辑',    3, '', '', 303, 1, '权限管理-编辑'),
(0, 'sys:permission:delete','权限删除',    3, '', '', 304, 1, '权限管理-删除');

-- 超级管理员数据范围：全部数据
INSERT INTO sys_data_scope (role_id, scope_type, resource_type, resource_id)
SELECT id, 1, '*', NULL FROM sys_role WHERE role_code = 'super_admin';

-- ---------------------------- 开发种子管理员 ----------------------------
-- 密码: Admin@123456（BCrypt 哈希；仅开发环境，生产必须禁用并走运维流程改密）
INSERT INTO user_account (username, nickname, user_type, status)
VALUES ('admin', '超级管理员', 3, 1);

INSERT INTO user_credential (user_id, credential_type, secret, status)
SELECT id, 1, '$2a$10$dgiSeRfXdJi4xq6jNubELulIg.c9rzdL64yeHsBPwz7La6MFB53gu', 1
FROM user_account WHERE username = 'admin';

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM user_account u, sys_role r
WHERE u.username = 'admin' AND r.role_code = 'super_admin';
