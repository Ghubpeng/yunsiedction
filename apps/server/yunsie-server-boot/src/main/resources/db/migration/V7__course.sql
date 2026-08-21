-- ============================================================================
-- V7: course 域（Stage 1.5 录播课程/视频/学习进度/续播）
-- 设计依据（已确认 N-1~N-10 + Stage 1.5 设计文档）：
--   * 课程 != 考试科目：course 为商业内容实体，经 course_lesson_knowledge_node 关联知识点/子知识点
--   * 录播 MVP：视频原文件直存 MinIO，不转码/HLS；播放接口返回短有效期预签名 URL
--   * 课程状态机：1-草稿 2-已发布 3-已下架；已发布禁止编辑/删除；
--     发布前置：至少 1 个启用章节 且 至少 1 个启用小节
--   * 章节/小节：status 1-启用 0-禁用；已发布课程中禁用节点对学员隐藏
--   * 学习进度：(user_id, lesson_id) 唯一 upsert；position 按 duration 截断；
--     finished = position >= duration * 95%（阈值配置化，见 application.yml）
--   * 商业解锁/价格/VIP 字段不入本域（pay 域职责）
--   * 直播：type=2 预留扩展位，不实现
-- 规范：database-design（域前缀/必备字段/无物理外键/唯一索引含 deleted/TINYINT 枚举注释）
-- ============================================================================

CREATE TABLE course_course (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    certificate_id BIGINT UNSIGNED NOT NULL COMMENT '所属证书ID',
    teacher_id     BIGINT UNSIGNED NOT NULL COMMENT '教师用户ID(user域, 无物理外键; 教师数据范围依据)',
    title          VARCHAR(100)    NOT NULL COMMENT '课程标题',
    description    VARCHAR(1000)   NOT NULL DEFAULT '' COMMENT '课程简介',
    type           TINYINT         NOT NULL DEFAULT 1 COMMENT '类型: 1-录播 2-直播(预留扩展位, 不实现)',
    status         TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1-草稿 2-已发布 3-已下架',
    cover_key      VARCHAR(255)    NULL COMMENT '封面对象键(预留, MVP 不实现封面上传)',
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_course_course_cert_status (certificate_id, status),
    KEY idx_course_course_teacher (teacher_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '课程表(商业内容实体; 课程!=考试科目; 无价格字段, 商业规则归 pay 域)';

CREATE TABLE course_chapter (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    course_id   BIGINT UNSIGNED NOT NULL COMMENT '所属课程ID',
    title       VARCHAR(100)    NOT NULL COMMENT '章节标题',
    sort        INT             NOT NULL DEFAULT 0 COMMENT '排序(越小越靠前)',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 0-禁用(已发布课程中禁用对学员隐藏)',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version     INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_course_chapter_course (course_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '课程章节表(商业章节; 与知识体系章节分离)';

CREATE TABLE course_lesson (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    chapter_id       BIGINT UNSIGNED NOT NULL COMMENT '所属章节ID',
    title            VARCHAR(100)    NOT NULL COMMENT '小节标题',
    video_key        VARCHAR(500)    NULL COMMENT '视频对象键(MinIO; NULL=未上传)',
    duration_seconds INT             NOT NULL DEFAULT 0 COMMENT '视频时长(秒, 管理员填写; 进度截断依据)',
    sort             INT             NOT NULL DEFAULT 0 COMMENT '排序(越小越靠前)',
    status           TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 0-禁用(已发布课程中禁用对学员隐藏)',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version          INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_lesson_video (video_key, deleted),
    KEY idx_course_lesson_chapter (chapter_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '课程小节表(视频原文件存 MinIO; 不转码)';

CREATE TABLE course_lesson_knowledge_node (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    lesson_id   BIGINT UNSIGNED NOT NULL COMMENT '小节ID',
    node_id     BIGINT UNSIGNED NOT NULL COMMENT '知识节点ID(subject域; 仅知识点/子知识点, 经 SubjectQueryApi 校验)',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version     INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_lesson_knowledge_node (lesson_id, node_id, deleted),
    KEY idx_course_lesson_knowledge_node_node (node_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '小节-知识节点关联表(可选关联; 节点数据归 subject 域)';

CREATE TABLE course_learn_progress (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id           BIGINT UNSIGNED NOT NULL COMMENT '用户ID(user域)',
    lesson_id         BIGINT UNSIGNED NOT NULL COMMENT '小节ID',
    position_seconds  INT             NOT NULL DEFAULT 0 COMMENT '已观看秒数(按 duration 截断)',
    duration_seconds  INT             NOT NULL DEFAULT 0 COMMENT '时长快照(上报时的小节时长)',
    finished          TINYINT         NOT NULL DEFAULT 0 COMMENT '是否完成: 1-是 0-否(position>=duration*阈值)',
    last_learn_time   DATETIME        NULL COMMENT '最近学习时间(续播排序依据)',
    create_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_learn_progress (user_id, lesson_id, deleted),
    KEY idx_course_learn_progress_user_time (user_id, last_learn_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学习进度表(自动续播依据; upsert 幂等)';

-- ---------------------------- RBAC 权限点种子 ----------------------------

INSERT INTO sys_permission (parent_id, permission_code, permission_name, perm_type, path, icon, sort, status, remark) VALUES
(0, 'course:course:create', '课程新增', 3, '', '', 801, 1, '课程-新增'),
(0, 'course:course:update', '课程编辑', 3, '', '', 802, 1, '课程-编辑(已发布禁止)'),
(0, 'course:course:delete', '课程删除', 3, '', '', 803, 1, '课程-删除(已发布禁止)'),
(0, 'course:course:read',   '课程查看', 3, '', '', 804, 1, '课程-列表/详情'),
(0, 'course:course:publish','课程发布', 3, '', '', 805, 1, '课程-发布/下架'),
(0, 'course:video:upload',  '视频上传', 3, '', '', 806, 1, '课程-视频上传(MinIO)');
