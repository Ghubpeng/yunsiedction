-- ============================================================================
-- V12: Stage 2.3A 内容管理生命周期完善（CONFLICTS #28）
-- 设计依据（Stage 2.3A 指令）：
--   * 学习章节管理：复用 subject_knowledge_node 章节层（node_type=1）承载「学习章节」，
--     新增 description 字段（name/description/sort 三字段与指令一致）；
--   * 课程创建四级归属（证书→版本→科目→章节）：course_course 新增 chapter_id 归属学习章节，
--     章节/科目删除前由后端守卫检查关联（禁止孤立课程、删除保护历史数据）。
-- 均为追加式（新列可空），不改 V1~V11 语义。
-- ============================================================================

ALTER TABLE subject_knowledge_node
    ADD COLUMN description VARCHAR(500) NULL COMMENT '节点描述(学习章节/知识点说明; Stage 2.3A)' AFTER name;

ALTER TABLE course_course
    ADD COLUMN chapter_id BIGINT UNSIGNED NULL COMMENT '归属学习章节ID(subject域知识节点 node_type=1; 内容树: 证书→版本→科目→章节→课程)',
    ADD KEY idx_course_course_chapter (chapter_id);
