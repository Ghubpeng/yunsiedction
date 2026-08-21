-- ============================================================================
-- V13: Stage 2.4 用户学习路径产品化（CONFLICTS #30）
-- 设计依据（Stage 2.4 指令）：
--   * 章节学习状态四态：未开始/学习中/已完成/掌握；
--     掌握 = 视频全部完成（course_learn_progress.finished） + 章节练习完成（本表）。
--   * 章节练习完成记录（user x chapter 唯一 upsert）：最近一次得分/正确数/题数，
--     供课程树（ChapterVO.practiceScore）与学习路径（/course/me/learning-path）使用。
-- 均为追加式（新表），不改 V1~V12 语义。
-- ============================================================================

CREATE TABLE course_chapter_practice (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          BIGINT UNSIGNED NOT NULL COMMENT '用户ID(user域, 无物理外键)',
    chapter_id       BIGINT UNSIGNED NOT NULL COMMENT '课程章节ID(course_chapter, 无物理外键)',
    correct_count    INT             NOT NULL DEFAULT 0 COMMENT '最近一次章节练习答对题数',
    total_count      INT             NOT NULL DEFAULT 0 COMMENT '最近一次章节练习题数',
    score            INT             NOT NULL DEFAULT 0 COMMENT '得分(0~100, 正确率四舍五入)',
    finished         TINYINT         NOT NULL DEFAULT 1 COMMENT '是否完成过章节练习: 1-是(有记录即完成)',
    last_practice_at DATETIME        NULL COMMENT '最近练习时间',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version          INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_chapter_practice_user (user_id, chapter_id, deleted),
    KEY idx_chapter_practice_chapter (chapter_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '章节练习完成记录(掌握状态=视频完成+本表记录; Stage 2.4)';
