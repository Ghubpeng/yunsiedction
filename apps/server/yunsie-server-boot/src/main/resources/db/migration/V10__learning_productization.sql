-- ============================================================================
-- V10: Stage 2.1 学习产品化 —— 内容树归属 + 用户考试目标（CONFLICTS #26）
-- 设计依据（Stage 2.1 指令：P0 统一内容树 / P0 用户考试目标体系）：
--   * 现有 course/exam 仅 certificate_id 一维归属，无法表达「证书→版本→科目」内容树关系；
--     前台按目标过滤课程/考试、后台内容树体现，均需 subject/version 归属。
--   * 用户「考试目标」为用户级学习上下文，现有表（证书/科目=公共数据、学习档案=聚合数据）无此概念。
-- 均为追加式：新列可空（旧数据不破坏）、新表独立；复用现有 ID 关系，不重建证书/科目体系；
-- 不含任何 pay 字段（pay 域仍后置）；不改 V1~V9 语义。
-- ============================================================================

ALTER TABLE course_course
    ADD COLUMN subject_id BIGINT UNSIGNED NULL COMMENT '归属科目ID(subject域; 内容树: 证书→版本→科目→课程; 空=未指定)',
    ADD COLUMN version_id BIGINT UNSIGNED NULL COMMENT '归属版本ID(subject域; 创建时默认证书当前版本; 空=未指定)',
    ADD KEY idx_course_course_subject (subject_id),
    ADD KEY idx_course_course_version (version_id);

ALTER TABLE exam_exam
    ADD COLUMN subject_id BIGINT UNSIGNED NULL COMMENT '归属科目ID(subject域; 内容树归属; 空=仅证书级)',
    ADD COLUMN version_id BIGINT UNSIGNED NULL COMMENT '归属版本ID(subject域; 内容树归属; 空=未指定)',
    ADD KEY idx_exam_exam_subject (subject_id),
    ADD KEY idx_exam_exam_version (version_id);

CREATE TABLE learn_user_goal (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT UNSIGNED NOT NULL COMMENT '用户ID(user域)',
    certificate_id BIGINT UNSIGNED NOT NULL COMMENT '考试目标证书ID(certificate域)',
    subject_id     BIGINT UNSIGNED NOT NULL COMMENT '考试科目ID(subject域)',
    version_id     BIGINT UNSIGNED NOT NULL COMMENT '目标版本ID(subject域; 选择时=证书当前版本)',
    status         TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1-当前目标 0-历史目标(切换后保留, 不删历史学习数据)',
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learn_user_goal (user_id, certificate_id, subject_id, deleted),
    KEY idx_learn_user_goal_user_status (user_id, status, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户考试目标表(学习上下文; 切换=状态流转, 历史目标保留)';
