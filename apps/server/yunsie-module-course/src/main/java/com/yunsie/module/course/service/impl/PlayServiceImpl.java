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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 播放服务：服务端校验（课程已发布 + 小节启用 + 已上传视频）后签发短有效期预签名 URL。
 * 客户端时间/直接拼 URL 一律无效（仅在线观看，MVP 不实现复杂防盗链）。
 */
@Service
@RequiredArgsConstructor
public class PlayServiceImpl {

    private final CourseLessonMapper lessonMapper;
    private final CourseChapterMapper chapterMapper;
    private final CourseMapper courseMapper;
    private final MinioStorageService storageService;

    @Value("${yunsie.course.play-url-expiry-seconds:900}")
    private int playUrlExpirySeconds;

    public PlayVO play(Long userId, Long lessonId) {
        CourseLesson lesson = lessonMapper.selectById(lessonId);
        if (lesson == null) {
            throw new BizException(CourseErrorCode.LESSON_NOT_FOUND);
        }
        CourseChapter chapter = chapterMapper.selectById(lesson.getChapterId());
        if (chapter == null) {
            throw new BizException(CourseErrorCode.CHAPTER_NOT_FOUND);
        }
        if (chapter.getStatus() == null || chapter.getStatus() != 1) {
            // 禁用章节对学员隐藏（决策 16）：播放同样拒绝
            throw new BizException(CourseErrorCode.LESSON_NOT_PLAYABLE);
        }
        Course course = courseMapper.selectById(chapter.getCourseId());
        if (course == null || CourseStatus.of(course.getStatus()) != CourseStatus.PUBLISHED) {
            throw new BizException(CourseErrorCode.LESSON_NOT_PLAYABLE);
        }
        if (lesson.getStatus() == null || lesson.getStatus() != 1) {
            throw new BizException(CourseErrorCode.LESSON_NOT_PLAYABLE);
        }
        if (lesson.getVideoKey() == null || lesson.getVideoKey().isBlank()) {
            throw new BizException(CourseErrorCode.VIDEO_NOT_UPLOADED);
        }
        String url = storageService.presignedGetUrl(lesson.getVideoKey(), playUrlExpirySeconds);
        return new PlayVO(url, playUrlExpirySeconds, LocalDateTime.now().plusSeconds(playUrlExpirySeconds));
    }
}
