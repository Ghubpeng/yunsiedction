-- ============================================================================
-- V11: Stage 2.2 用户体验优化与商业入口基础（CONFLICTS #27）
-- 设计依据（Stage 2.2 指令）：
--   * 用户考试目标降门槛：用户只选证书 → 证书级目标（subject_id=0 哨兵表示「证书级·自动覆盖全部科目」）。
--     保留 subject/version 模型与字段，不删除任何已有结构；科目级目标（subject_id>0）能力继续保留。
--   * 联系客服配置化：sys 域无任何配置设施（CONFLICTS #19 已记）→ 最小单行配置表 sys_customer_service
--     （不接第三方客服 SDK，仅电话/微信/二维码/服务时间/开关）。
-- 均为追加式，不改 V1~V10 语义。
-- ============================================================================

-- 客服配置权限点（超级管理员经 *:*:* 通配自动拥有；后台菜单按此码渲染）
INSERT INTO sys_permission (parent_id, permission_code, permission_name, perm_type, path, icon, sort, status, remark)
VALUES (0, 'sys:service:config', '客服配置', 3, '', '', 110, 1, '客服联系方式配置（电话/微信/二维码/服务时间/开关）');

CREATE TABLE sys_customer_service (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键(单行配置, 固定 id=1)',
    phone         VARCHAR(30)     NULL COMMENT '客服电话(空=未配置)',
    wechat        VARCHAR(50)     NULL COMMENT '客服微信号(空=未配置)',
    qr_code_url   VARCHAR(500)    NULL COMMENT '客服微信二维码图片URL(空=未配置)',
    service_time  VARCHAR(100)    NULL COMMENT '服务时间文案(如 工作日 9:00-18:00)',
    enabled       TINYINT         NOT NULL DEFAULT 1 COMMENT '是否启用客服入口: 0-关闭 1-开启',
    create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version       INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客服联系方式配置表(单行; 配置化, 不接第三方客服系统)';

INSERT INTO sys_customer_service (id, phone, wechat, qr_code_url, service_time, enabled)
VALUES (1, '', '', '', '工作日 9:00-18:00', 1);
