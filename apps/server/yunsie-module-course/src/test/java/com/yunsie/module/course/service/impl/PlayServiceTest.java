package com.yunsie.module.course.service.impl;

import com.yunsie.common.exception.BizException;
import com.yunsie.module.course.entity.Course;
import com.yunsie.module.course.entity.CourseChapter;
import com.yunsie.module.course.entity.CourseLesson;
import com.yunsie.module.course.enums.CourseStatus;
import com.yunsie.module.course.error.CourseErrorCode;
import com.yunsie.module.course.mapper.CourseChapterMapper;
import com.yunsie.module.course.mapper.CourseLessonMapper;
import com.yunsie.module.course.mapper.CourseMapper;
import com.yunsie.module.course.service.MinioStorageService;
import com.yunsie.module.course.vo.PlayVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 播放服务单元测试：未发布/禁用/未上传拒绝；正常签发预签名 URL。
 */
class PlayServiceTest {

    private PlayServiceImpl service(CourseLessonMapper lessonMapper, CourseChapterMapper chapterMapper,
                                    CourseMapper courseMapper, MinioStorageService storageService) {
        PlayServiceImpl service = new PlayServiceImpl(lessonMapper, chapterMapper, courseMapper, storageService);
        ReflectionTestUtils.setField(service, "playUrlExpirySeconds", 900);
        return service;
    }

    private void stubChain(CourseLessonMapper lessonMapper, CourseChapterMapper chapterMapper,
                           CourseMapper courseMapper, int chapterStatus, int lessonStatus, int courseStatus,
                           String videoKey) {
        CourseLesson lesson = new CourseLesson();
        lesson.setId(5L);
        lesson.setChapterId(4L);
        lesson.setStatus(lessonStatus);
        lesson.setVideoKey(videoKey);
        when(lessonMapper.selectById(5L)).thenReturn(lesson);
        CourseChapter chapter = new CourseChapter();
        chapter.setId(4L);
        chapter.setCourseId(3L);
        chapter.setStatus(chapterStatus);
        when(chapterMapper.selectById(4L)).thenReturn(chapter);
        Course course = new Course();
        course.setId(3L);
        course.setStatus(courseStatus);
        when(courseMapper.selectById(3L)).thenReturn(course);
    }

    @Test
    void play_publishedAndEnabled_returnsPresignedUrl() {
        CourseLessonMapper lessonMapper = mock(CourseLessonMapper.class);
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        stubChain(lessonMapper, chapterMapper, courseMapper, 1, 1, CourseStatus.PUBLISHED.code(), "course/3/5/v.mp4");
        MinioStorageService storageService = mock(MinioStorageService.class);
        when(storageService.presignedGetUrl("course/3/5/v.mp4", 900)).thenReturn("http://minio/presigned");

        PlayVO vo = service(lessonMapper, chapterMapper, courseMapper, storageService).play(7L, 5L);

        assertTrue(vo.url().startsWith("http://minio/"));
        assertEquals(900, vo.expiresInSeconds());
        verify(storageService).presignedGetUrl("course/3/5/v.mp4", 900);
    }

    @Test
    void play_disabledChapter_rejected() {
        CourseLessonMapper lessonMapper = mock(CourseLessonMapper.class);
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        stubChain(lessonMapper, chapterMapper, courseMapper, 0, 1, CourseStatus.PUBLISHED.code(), "v.mp4");

        BizException ex = assertThrows(BizException.class,
                () -> service(lessonMapper, chapterMapper, courseMapper, mock(MinioStorageService.class))
                        .play(7L, 5L));
        assertEquals(CourseErrorCode.LESSON_NOT_PLAYABLE.code(), ex.getCode());
    }

    @Test
    void play_unpublishedCourse_rejected() {
        CourseLessonMapper lessonMapper = mock(CourseLessonMapper.class);
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        stubChain(lessonMapper, chapterMapper, courseMapper, 1, 1, CourseStatus.DRAFT.code(), "v.mp4");

        BizException ex = assertThrows(BizException.class,
                () -> service(lessonMapper, chapterMapper, courseMapper, mock(MinioStorageService.class))
                        .play(7L, 5L));
        assertEquals(CourseErrorCode.LESSON_NOT_PLAYABLE.code(), ex.getCode());
    }

    @Test
    void play_disabledLesson_rejected() {
        CourseLessonMapper lessonMapper = mock(CourseLessonMapper.class);
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        stubChain(lessonMapper, chapterMapper, courseMapper, 1, 0, CourseStatus.PUBLISHED.code(), "v.mp4");

        BizException ex = assertThrows(BizException.class,
                () -> service(lessonMapper, chapterMapper, courseMapper, mock(MinioStorageService.class))
                        .play(7L, 5L));
        assertEquals(CourseErrorCode.LESSON_NOT_PLAYABLE.code(), ex.getCode());
    }

    @Test
    void play_noVideo_rejected() {
        CourseLessonMapper lessonMapper = mock(CourseLessonMapper.class);
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        stubChain(lessonMapper, chapterMapper, courseMapper, 1, 1, CourseStatus.PUBLISHED.code(), null);

        BizException ex = assertThrows(BizException.class,
                () -> service(lessonMapper, chapterMapper, courseMapper, mock(MinioStorageService.class))
                        .play(7L, 5L));
        assertEquals(CourseErrorCode.VIDEO_NOT_UPLOADED.code(), ex.getCode());
    }
}
