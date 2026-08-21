package com.yunsie.module.course.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * course 域对外最小契约（供 pay 解锁判定、ai course.chat 上下文、learning-profile 使用）。
 * 返回不可变 View DTO；其他域禁止直连 course 域 Entity/Mapper/表（模块边界铁律）。
 *
 * <p>Stage 1.6 追加式扩展（仅新增方法/View，不改已有方法行为）：
 * 学习进度列表 / 教师课程归属 / 学员课程列表，供 learning-profile 聚合与教师 DataScope 判定。</p>
 */
public interface CourseQueryApi {

    /** 已发布课程视图（null=不存在/未发布/已删除） */
    CourseView findPublishedCourse(Long courseId);

    /** 任意状态课程视图（管理/校验用） */
    CourseView findCourse(Long courseId);

    LessonView findLesson(Long lessonId);

    /** 小节可学习：存在 + 启用 + 所属课程已发布 */
    boolean existsPublishedLesson(Long lessonId);

    /** 用户全部课程学习进度（空列表=无） */
    List<ProgressView> listLearnerProgress(Long userId);

    /** 教师拥有的课程 ID 列表（教师数据范围判定依据；空列表=无） */
    List<Long> listTeacherCourseIds(Long teacherId);

    /** 学员已产生学习进度的课程 ID 去重列表（空列表=无） */
    List<Long> listLearnerCourseIds(Long userId);

    /** 某课程下产生过学习进度的学员 ID 去重列表（教师查看学员档案用；空列表=无） */
    List<Long> listLearnerIdsByCourse(Long courseId);

    /** 章节下全部小节关联的知识点节点 ID 去重（空列表=无关联） */
    List<Long> chapterNodeIds(Long chapterId);

    /** 归属该科目的课程数（Stage 2.3A 科目删除守卫） */
    long countBySubject(Long subjectId);

    /** 归属该学习章节的课程数（Stage 2.3A 章节删除守卫） */
    long countByChapter(Long chapterNodeId);

    /** 课程总数（Stage 2.3B 运营看板） */
    long countAll();

    /** 已上传视频的小节数（video_key 非空；Stage 2.3B 运营看板） */
    long countLessonsWithVideo();

    /** 空章节（课程章节下无任何小节；Stage 2.3B 运营提醒） */
    List<EmptyChapterView> listEmptyChapters(int limit);

    /** 已被课程归属的学习章节节点 ID 去重（Stage 2.3B 运营提醒：无课程章节） */
    List<Long> listUsedChapterIds();

    /** 关联给定知识点节点的已发布课程 ID 去重（Stage 2.3B AI 规则推荐课程） */
    List<Long> listPublishedCourseIdsByNodeIds(java.util.Collection<Long> nodeIds);

    /** 窗口期内完成（finished=1 且 last_learn_time>=since）的课时所属章节去重数（Stage 2.4 周报告） */
    long countFinishedChaptersInWindow(Long userId, java.time.LocalDateTime since);

    record CourseView(Long id, Long certificateId, Long teacherId, String title,
                      String description, Integer type, Integer status) {
    }

    record LessonView(Long id, Long chapterId, Long courseId, String title,
                      String videoKey, Integer durationSeconds, Integer status) {
    }

    record ProgressView(Long lessonId, Long courseId, Integer positionSeconds,
                        Integer durationSeconds, Integer finished, LocalDateTime lastLearnTime) {
    }

    record EmptyChapterView(Long courseId, String courseTitle, Long chapterId, String chapterTitle) {
    }
}
