package com.yunsie.module.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.course.dto.ProgressReq;
import com.yunsie.module.course.entity.Course;
import com.yunsie.module.course.entity.CourseChapter;
import com.yunsie.module.course.entity.CourseLearnProgress;
import com.yunsie.module.course.entity.CourseLesson;
import com.yunsie.module.course.enums.CourseStatus;
import com.yunsie.module.course.mapper.CourseChapterMapper;
import com.yunsie.module.course.mapper.CourseLearnProgressMapper;
import com.yunsie.module.course.mapper.CourseLessonMapper;
import com.yunsie.module.course.mapper.CourseMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 进度单元测试：upsert / position 截断 / 95% 阈值判定（阈值经配置注入）。
 */
class ProgressServiceTest {

    private ProgressServiceImpl service(CourseLearnProgressMapper progressMapper,
                                        CourseLessonMapper lessonMapper,
                                        CourseChapterMapper chapterMapper,
                                        CourseMapper courseMapper) {
        ProgressServiceImpl service = new ProgressServiceImpl(progressMapper, lessonMapper, chapterMapper, courseMapper);
        ReflectionTestUtils.setField(service, "finishThreshold", 0.95);
        return service;
    }

    private void stubChain(CourseLessonMapper lessonMapper, CourseChapterMapper chapterMapper,
                           CourseMapper courseMapper, int durationSeconds, int lessonStatus, int courseStatus) {
        CourseLesson lesson = new CourseLesson();
        lesson.setId(5L);
        lesson.setChapterId(4L);
        lesson.setDurationSeconds(durationSeconds);
        lesson.setStatus(lessonStatus);
        when(lessonMapper.selectById(5L)).thenReturn(lesson);
        CourseChapter chapter = new CourseChapter();
        chapter.setId(4L);
        chapter.setCourseId(3L);
        chapter.setStatus(1);
        when(chapterMapper.selectById(4L)).thenReturn(chapter);
        Course course = new Course();
        course.setId(3L);
        course.setStatus(courseStatus);
        when(courseMapper.selectById(3L)).thenReturn(course);
    }

    @Test
    void upsert_insertsNewProgress() {
        CourseLearnProgressMapper progressMapper = mock(CourseLearnProgressMapper.class);
        CourseLessonMapper lessonMapper = mock(CourseLessonMapper.class);
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        stubChain(lessonMapper, chapterMapper, courseMapper, 100, 1, CourseStatus.PUBLISHED.code());
        when(progressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        service(progressMapper, lessonMapper, chapterMapper, courseMapper)
                .upsert(7L, new ProgressReq(5L, 60));

        ArgumentCaptor<CourseLearnProgress> captor = ArgumentCaptor.forClass(CourseLearnProgress.class);
        verify(progressMapper).insert(captor.capture());
        assertEquals(60, captor.getValue().getPositionSeconds());
        assertEquals(0, captor.getValue().getFinished());
    }

    @Test
    void upsert_positionClamped() {
        CourseLearnProgressMapper progressMapper = mock(CourseLearnProgressMapper.class);
        CourseLessonMapper lessonMapper = mock(CourseLessonMapper.class);
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        stubChain(lessonMapper, chapterMapper, courseMapper, 100, 1, CourseStatus.PUBLISHED.code());
        CourseLearnProgress existing = new CourseLearnProgress();
        existing.setId(1L);
        when(progressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        service(progressMapper, lessonMapper, chapterMapper, courseMapper)
                .upsert(7L, new ProgressReq(5L, 9999));

        assertEquals(100, existing.getPositionSeconds(), "position 必须按 duration 截断");
    }

    @Test
    void upsert_finishedAtThreshold() {
        CourseLearnProgressMapper progressMapper = mock(CourseLearnProgressMapper.class);
        CourseLessonMapper lessonMapper = mock(CourseLessonMapper.class);
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        stubChain(lessonMapper, chapterMapper, courseMapper, 100, 1, CourseStatus.PUBLISHED.code());
        when(progressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        service(progressMapper, lessonMapper, chapterMapper, courseMapper)
                .upsert(7L, new ProgressReq(5L, 95));

        ArgumentCaptor<CourseLearnProgress> captor = ArgumentCaptor.forClass(CourseLearnProgress.class);
        verify(progressMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getFinished(), "95% 阈值：position>=95 应判定完成");
    }

    @Test
    void upsert_notPublishedCourse_rejected() {
        CourseLearnProgressMapper progressMapper = mock(CourseLearnProgressMapper.class);
        CourseLessonMapper lessonMapper = mock(CourseLessonMapper.class);
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        stubChain(lessonMapper, chapterMapper, courseMapper, 100, 1, CourseStatus.DRAFT.code());

        BizException ex = assertThrows(BizException.class,
                () -> service(progressMapper, lessonMapper, chapterMapper, courseMapper)
                        .upsert(7L, new ProgressReq(5L, 50)));
        assertEquals(30515, ex.getCode());
    }
}
