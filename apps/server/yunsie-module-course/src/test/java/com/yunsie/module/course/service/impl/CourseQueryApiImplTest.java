package com.yunsie.module.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.course.api.CourseQueryApi;
import com.yunsie.module.course.entity.Course;
import com.yunsie.module.course.entity.CourseChapter;
import com.yunsie.module.course.entity.CourseLearnProgress;
import com.yunsie.module.course.entity.CourseLesson;
import com.yunsie.module.course.mapper.CourseChapterMapper;
import com.yunsie.module.course.mapper.CourseLearnProgressMapper;
import com.yunsie.module.course.mapper.CourseLessonKnowledgeNodeMapper;
import com.yunsie.module.course.mapper.CourseLessonMapper;
import com.yunsie.module.course.mapper.CourseMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stage 1.6 契约扩展测试：学习进度/教师归属/学员列表的 View 映射（追加式，不改既有方法）。
 */
class CourseQueryApiImplTest {

    private final CourseMapper courseMapper = mock(CourseMapper.class);
    private final CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
    private final CourseLessonMapper lessonMapper = mock(CourseLessonMapper.class);
    private final CourseLessonKnowledgeNodeMapper lessonKnowledgeNodeMapper = mock(CourseLessonKnowledgeNodeMapper.class);
    private final CourseLearnProgressMapper progressMapper = mock(CourseLearnProgressMapper.class);

    private CourseQueryApiImpl api() {
        return new CourseQueryApiImpl(courseMapper, chapterMapper, lessonMapper,
                lessonKnowledgeNodeMapper, progressMapper);
    }

    private CourseLearnProgress progress(long lessonId, int position, int finished, LocalDateTime at) {
        CourseLearnProgress p = new CourseLearnProgress();
        p.setUserId(10L);
        p.setLessonId(lessonId);
        p.setPositionSeconds(position);
        p.setDurationSeconds(600);
        p.setFinished(finished);
        p.setLastLearnTime(at);
        return p;
    }

    @Test
    void listLearnerProgress_mapsLessonCourseChain() {
        when(progressMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                progress(201L, 570, 1, LocalDateTime.of(2026, 8, 19, 8, 0)),
                progress(202L, 100, 0, null)));
        CourseLesson l1 = new CourseLesson();
        l1.setId(201L);
        l1.setChapterId(301L);
        CourseLesson l2 = new CourseLesson();
        l2.setId(202L);
        l2.setChapterId(302L);
        when(lessonMapper.selectBatchIds(anyList())).thenReturn(List.of(l1, l2));
        CourseChapter c1 = new CourseChapter();
        c1.setId(301L);
        c1.setCourseId(7L);
        CourseChapter c2 = new CourseChapter();
        c2.setId(302L);
        c2.setCourseId(8L);
        when(chapterMapper.selectBatchIds(anyList())).thenReturn(List.of(c1, c2));

        List<CourseQueryApi.ProgressView> views = api().listLearnerProgress(10L);

        assertEquals(2, views.size());
        CourseQueryApi.ProgressView v1 = views.stream().filter(v -> v.lessonId() == 201L).findFirst().orElseThrow();
        assertEquals(7L, v1.courseId());
        assertEquals(570, v1.positionSeconds());
        assertEquals(1, v1.finished());
        CourseQueryApi.ProgressView v2 = views.stream().filter(v -> v.lessonId() == 202L).findFirst().orElseThrow();
        assertEquals(8L, v2.courseId());
        assertEquals(0, v2.finished());
    }

    @Test
    void listTeacherCourseIds_mapsOwnedCourses() {
        Course course = new Course();
        course.setId(7L);
        course.setTeacherId(2L);
        when(courseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(course));

        List<Long> ids = api().listTeacherCourseIds(2L);

        assertEquals(List.of(7L), ids);
    }

    @Test
    void listLearnerCourseIds_distinctCourses() {
        when(progressMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                progress(201L, 570, 1, LocalDateTime.now()),
                progress(202L, 100, 0, LocalDateTime.now())));
        CourseLesson l1 = new CourseLesson();
        l1.setId(201L);
        l1.setChapterId(301L);
        CourseLesson l2 = new CourseLesson();
        l2.setId(202L);
        l2.setChapterId(301L);
        when(lessonMapper.selectBatchIds(anyList())).thenReturn(List.of(l1, l2));
        CourseChapter c = new CourseChapter();
        c.setId(301L);
        c.setCourseId(7L);
        when(chapterMapper.selectBatchIds(anyList())).thenReturn(List.of(c));

        assertEquals(List.of(7L), api().listLearnerCourseIds(10L));
    }

    @Test
    void listLearnerIdsByCourse_distinctUsers() {
        CourseChapter c = new CourseChapter();
        c.setId(301L);
        c.setCourseId(7L);
        when(chapterMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(c));
        CourseLesson lesson = new CourseLesson();
        lesson.setId(201L);
        lesson.setChapterId(301L);
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(lesson));
        CourseLearnProgress p1 = progress(201L, 570, 1, LocalDateTime.now());
        p1.setUserId(10L);
        CourseLearnProgress p2 = progress(201L, 100, 0, LocalDateTime.now());
        p2.setUserId(11L);
        when(progressMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p1, p2));

        List<Long> learnerIds = api().listLearnerIdsByCourse(7L);

        assertEquals(2, learnerIds.size());
        assertTrue(learnerIds.contains(10L));
        assertTrue(learnerIds.contains(11L));
    }

    @Test
    void nullUserId_empty() {
        assertTrue(api().listLearnerProgress(null).isEmpty());
        assertTrue(api().listTeacherCourseIds(null).isEmpty());
        assertTrue(api().listLearnerCourseIds(null).isEmpty());
    }

    @Test
    void listEmptyChapters_chapterWithoutLessons() {
        CourseChapter chEmpty = new CourseChapter();
        chEmpty.setId(501L);
        chEmpty.setCourseId(7L);
        chEmpty.setTitle("空章节");
        chEmpty.setStatus(1);
        CourseChapter chFull = new CourseChapter();
        chFull.setId(502L);
        chFull.setCourseId(8L);
        chFull.setTitle("有小节章节");
        chFull.setStatus(1);
        when(chapterMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(chEmpty, chFull));
        CourseLesson lesson = new CourseLesson();
        lesson.setChapterId(502L);
        lesson.setId(601L);
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(lesson));
        Course course = new Course();
        course.setId(7L);
        course.setTitle("课程7");
        when(courseMapper.selectBatchIds(anyList())).thenReturn(List.of(course));

        List<CourseQueryApi.EmptyChapterView> views = api().listEmptyChapters(5);

        assertEquals(1, views.size());
        assertEquals(501L, views.get(0).chapterId());
        assertEquals("空章节", views.get(0).chapterTitle());
        assertEquals("课程7", views.get(0).courseTitle());
    }
}
