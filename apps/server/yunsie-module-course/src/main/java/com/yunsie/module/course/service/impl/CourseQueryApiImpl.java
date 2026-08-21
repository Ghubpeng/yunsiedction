package com.yunsie.module.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.course.api.CourseQueryApi;
import com.yunsie.module.course.entity.Course;
import com.yunsie.module.course.entity.CourseChapter;
import com.yunsie.module.course.entity.CourseLearnProgress;
import com.yunsie.module.course.entity.CourseLesson;
import com.yunsie.module.course.entity.CourseLessonKnowledgeNode;
import com.yunsie.module.course.enums.CourseStatus;
import com.yunsie.module.course.mapper.CourseChapterMapper;
import com.yunsie.module.course.mapper.CourseLearnProgressMapper;
import com.yunsie.module.course.mapper.CourseLessonKnowledgeNodeMapper;
import com.yunsie.module.course.mapper.CourseLessonMapper;
import com.yunsie.module.course.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * course 域对外最小契约实现（不可变 View DTO）。
 * Stage 1.6 追加：学习进度/教师归属/学员列表查询（仅新增，不改既有方法行为）。
 */
@Service
@RequiredArgsConstructor
public class CourseQueryApiImpl implements CourseQueryApi {

    private final CourseMapper courseMapper;
    private final CourseChapterMapper chapterMapper;
    private final CourseLessonMapper lessonMapper;
    private final CourseLessonKnowledgeNodeMapper lessonKnowledgeNodeMapper;
    private final CourseLearnProgressMapper progressMapper;

    @Override
    public CourseView findPublishedCourse(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null || CourseStatus.of(course.getStatus()) != CourseStatus.PUBLISHED) {
            return null;
        }
        return toCourseView(course);
    }

    @Override
    public CourseView findCourse(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        return course == null ? null : toCourseView(course);
    }

    @Override
    public LessonView findLesson(Long lessonId) {
        CourseLesson lesson = lessonMapper.selectById(lessonId);
        if (lesson == null) {
            return null;
        }
        CourseChapter chapter = chapterMapper.selectById(lesson.getChapterId());
        return new LessonView(lesson.getId(), lesson.getChapterId(),
                chapter == null ? null : chapter.getCourseId(),
                lesson.getTitle(), lesson.getVideoKey(), lesson.getDurationSeconds(), lesson.getStatus());
    }

    @Override
    public boolean existsPublishedLesson(Long lessonId) {
        LessonView lesson = findLesson(lessonId);
        if (lesson == null || lesson.status() == null || lesson.status() != 1 || lesson.courseId() == null) {
            return false;
        }
        return findPublishedCourse(lesson.courseId()) != null;
    }

    @Override
    public List<ProgressView> listLearnerProgress(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<CourseLearnProgress> rows = progressMapper.selectList(new LambdaQueryWrapper<CourseLearnProgress>()
                .eq(CourseLearnProgress::getUserId, userId));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> lessonIds = rows.stream().map(CourseLearnProgress::getLessonId).distinct().toList();
        Map<Long, CourseLesson> lessons = lessonMapper.selectBatchIds(lessonIds).stream()
                .collect(Collectors.toMap(CourseLesson::getId, Function.identity()));
        Map<Long, Long> courseByChapter = chapterCourseMap(lessons.values().stream()
                .map(CourseLesson::getChapterId).distinct().toList());
        return rows.stream()
                .map(r -> {
                    CourseLesson lesson = lessons.get(r.getLessonId());
                    Long courseId = lesson == null ? null : courseByChapter.get(lesson.getChapterId());
                    return new ProgressView(r.getLessonId(), courseId, r.getPositionSeconds(),
                            r.getDurationSeconds(), r.getFinished(), r.getLastLearnTime());
                })
                .toList();
    }

    @Override
    public List<Long> listTeacherCourseIds(Long teacherId) {
        if (teacherId == null) {
            return List.of();
        }
        return courseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .eq(Course::getTeacherId, teacherId))
                .stream().map(Course::getId).toList();
    }

    @Override
    public List<Long> listLearnerCourseIds(Long userId) {
        return listLearnerProgress(userId).stream()
                .map(ProgressView::courseId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public List<Long> listLearnerIdsByCourse(Long courseId) {
        if (courseId == null) {
            return List.of();
        }
        List<Long> lessonIds = lessonIdsOfCourse(courseId);
        if (lessonIds.isEmpty()) {
            return List.of();
        }
        return progressMapper.selectList(new LambdaQueryWrapper<CourseLearnProgress>()
                        .in(CourseLearnProgress::getLessonId, lessonIds))
                .stream().map(CourseLearnProgress::getUserId).distinct().toList();
    }

    @Override
    public List<Long> chapterNodeIds(Long chapterId) {
        if (chapterId == null) {
            return List.of();
        }
        List<Long> lessonIds = lessonMapper.selectList(new LambdaQueryWrapper<CourseLesson>()
                        .eq(CourseLesson::getChapterId, chapterId))
                .stream().map(CourseLesson::getId).toList();
        if (lessonIds.isEmpty()) {
            return List.of();
        }
        return lessonKnowledgeNodeMapper.selectList(new LambdaQueryWrapper<CourseLessonKnowledgeNode>()
                        .in(CourseLessonKnowledgeNode::getLessonId, lessonIds))
                .stream().map(CourseLessonKnowledgeNode::getNodeId).distinct().toList();
    }

    @Override
    public long countBySubject(Long subjectId) {
        if (subjectId == null) {
            return 0;
        }
        Long count = courseMapper.selectCount(new LambdaQueryWrapper<Course>()
                .eq(Course::getSubjectId, subjectId));
        return count == null ? 0 : count;
    }

    @Override
    public long countByChapter(Long chapterNodeId) {
        if (chapterNodeId == null) {
            return 0;
        }
        Long count = courseMapper.selectCount(new LambdaQueryWrapper<Course>()
                .eq(Course::getChapterId, chapterNodeId));
        return count == null ? 0 : count;
    }

    @Override
    public long countAll() {
        Long count = courseMapper.selectCount(new LambdaQueryWrapper<Course>());
        return count == null ? 0 : count;
    }

    @Override
    public long countLessonsWithVideo() {
        Long count = lessonMapper.selectCount(new LambdaQueryWrapper<CourseLesson>()
                .isNotNull(CourseLesson::getVideoKey)
                .ne(CourseLesson::getVideoKey, ""));
        return count == null ? 0 : count;
    }

    @Override
    public List<EmptyChapterView> listEmptyChapters(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<CourseChapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<CourseChapter>()
                .eq(CourseChapter::getStatus, 1)
                .orderByAsc(CourseChapter::getId)
                .last("LIMIT " + safeLimit * 2));
        if (chapters.isEmpty()) {
            return List.of();
        }
        List<Long> chapterIds = chapters.stream().map(CourseChapter::getId).toList();
        List<Long> lessonChapterIds = lessonMapper.selectList(new LambdaQueryWrapper<CourseLesson>()
                        .in(CourseLesson::getChapterId, chapterIds))
                .stream().map(CourseLesson::getChapterId).distinct().toList();
        List<Long> emptyIds = chapters.stream().map(CourseChapter::getId)
                .filter(id -> !lessonChapterIds.contains(id)).limit(safeLimit).toList();
        if (emptyIds.isEmpty()) {
            return List.of();
        }
        Map<Long, CourseChapter> byId = chapters.stream()
                .collect(Collectors.toMap(CourseChapter::getId, Function.identity()));
        List<Long> courseIds = emptyIds.stream()
                .map(id -> byId.get(id).getCourseId()).distinct().toList();
        Map<Long, Course> courses = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        return emptyIds.stream()
                .map(id -> {
                    CourseChapter ch = byId.get(id);
                    Course course = courses.get(ch.getCourseId());
                    return new EmptyChapterView(ch.getCourseId(), course == null ? null : course.getTitle(),
                            ch.getId(), ch.getTitle());
                })
                .toList();
    }

    @Override
    public List<Long> listUsedChapterIds() {
        return courseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .isNotNull(Course::getChapterId))
                .stream().map(Course::getChapterId).distinct().toList();
    }

    @Override
    public List<Long> listPublishedCourseIdsByNodeIds(java.util.Collection<Long> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return List.of();
        }
        List<Long> lessonIds = lessonKnowledgeNodeMapper.selectList(
                        new LambdaQueryWrapper<CourseLessonKnowledgeNode>()
                                .in(CourseLessonKnowledgeNode::getNodeId, nodeIds))
                .stream().map(CourseLessonKnowledgeNode::getLessonId).distinct().toList();
        if (lessonIds.isEmpty()) {
            return List.of();
        }
        List<Long> chapterIds = lessonMapper.selectList(new LambdaQueryWrapper<CourseLesson>()
                        .in(CourseLesson::getId, lessonIds))
                .stream().map(CourseLesson::getChapterId).distinct().toList();
        if (chapterIds.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = chapterMapper.selectList(new LambdaQueryWrapper<CourseChapter>()
                        .in(CourseChapter::getId, chapterIds))
                .stream().map(CourseChapter::getCourseId).distinct().toList();
        if (courseIds.isEmpty()) {
            return List.of();
        }
        return courseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .in(Course::getId, courseIds)
                        .eq(Course::getStatus, CourseStatus.PUBLISHED.code()))
                .stream().map(Course::getId).toList();
    }

    @Override
    public long countFinishedChaptersInWindow(Long userId, java.time.LocalDateTime since) {
        if (userId == null || since == null) {
            return 0;
        }
        List<Long> lessonIds = progressMapper.selectList(new LambdaQueryWrapper<CourseLearnProgress>()
                        .eq(CourseLearnProgress::getUserId, userId)
                        .eq(CourseLearnProgress::getFinished, 1)
                        .ge(CourseLearnProgress::getLastLearnTime, since))
                .stream().map(CourseLearnProgress::getLessonId).distinct().toList();
        if (lessonIds.isEmpty()) {
            return 0;
        }
        List<Long> chapterIds = lessonMapper.selectList(new LambdaQueryWrapper<CourseLesson>()
                        .in(CourseLesson::getId, lessonIds))
                .stream().map(CourseLesson::getChapterId).distinct().toList();
        if (chapterIds.isEmpty()) {
            return 0;
        }
        Long count = chapterMapper.selectCount(new LambdaQueryWrapper<CourseChapter>()
                .in(CourseChapter::getId, chapterIds));
        return count == null ? 0 : count;
    }

    private Map<Long, Long> chapterCourseMap(List<Long> chapterIds) {
        if (chapterIds.isEmpty()) {
            return Map.of();
        }
        return chapterMapper.selectBatchIds(chapterIds).stream()
                .collect(Collectors.toMap(CourseChapter::getId, CourseChapter::getCourseId, (a, b) -> a));
    }

    private List<Long> lessonIdsOfCourse(Long courseId) {
        List<Long> chapterIds = chapterMapper.selectList(new LambdaQueryWrapper<CourseChapter>()
                        .eq(CourseChapter::getCourseId, courseId))
                .stream().map(CourseChapter::getId).toList();
        if (chapterIds.isEmpty()) {
            return List.of();
        }
        return lessonMapper.selectList(new LambdaQueryWrapper<CourseLesson>()
                        .in(CourseLesson::getChapterId, chapterIds))
                .stream().map(CourseLesson::getId).toList();
    }

    private CourseView toCourseView(Course course) {
        return new CourseView(course.getId(), course.getCertificateId(), course.getTeacherId(),
                course.getTitle(), course.getDescription(), course.getType(), course.getStatus());
    }
}
