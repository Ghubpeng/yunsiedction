package com.yunsie.module.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.course.dto.ProgressReq;
import com.yunsie.module.course.entity.Course;
import com.yunsie.module.course.entity.CourseChapter;
import com.yunsie.module.course.entity.CourseLearnProgress;
import com.yunsie.module.course.entity.CourseLesson;
import com.yunsie.module.course.enums.CourseStatus;
import com.yunsie.module.course.error.CourseErrorCode;
import com.yunsie.module.course.mapper.CourseChapterMapper;
import com.yunsie.module.course.mapper.CourseLearnProgressMapper;
import com.yunsie.module.course.mapper.CourseLessonMapper;
import com.yunsie.module.course.mapper.CourseMapper;
import com.yunsie.module.course.vo.MyCourseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习进度服务：(user_id, lesson_id) 唯一 upsert；
 * position 按 duration 截断；finished = position >= duration * 阈值（阈值配置化，application.yml）。
 */
@Service
@RequiredArgsConstructor
public class ProgressServiceImpl {

    private final CourseLearnProgressMapper progressMapper;
    private final CourseLessonMapper lessonMapper;
    private final CourseChapterMapper chapterMapper;
    private final CourseMapper courseMapper;

    /** 完成阈值（配置化，禁止散落硬编码） */
    @Value("${yunsie.course.finish-threshold:0.95}")
    private double finishThreshold;

    @Transactional(rollbackFor = Exception.class)
    public void upsert(Long userId, ProgressReq req) {
        CourseLesson lesson = lessonMapper.selectById(req.lessonId());
        if (lesson == null) {
            throw new BizException(CourseErrorCode.LESSON_NOT_FOUND);
        }
        CourseChapter chapter = chapterMapper.selectById(lesson.getChapterId());
        if (chapter == null) {
            throw new BizException(CourseErrorCode.CHAPTER_NOT_FOUND);
        }
        if (chapter.getStatus() == null || chapter.getStatus() != 1) {
            // 禁用章节对学员隐藏（决策 16）：进度上报同样拒绝
            throw new BizException(CourseErrorCode.LESSON_NOT_PLAYABLE);
        }
        Course course = courseMapper.selectById(chapter.getCourseId());
        if (course == null || CourseStatus.of(course.getStatus()) != CourseStatus.PUBLISHED) {
            throw new BizException(CourseErrorCode.COURSE_NOT_PUBLISHED);
        }
        if (lesson.getStatus() == null || lesson.getStatus() != 1) {
            throw new BizException(CourseErrorCode.LESSON_NOT_PLAYABLE);
        }
        int duration = lesson.getDurationSeconds() == null ? 0 : lesson.getDurationSeconds();
        int position = Math.max(0, req.positionSeconds());
        if (duration > 0) {
            position = Math.min(position, duration); // 截断
        }
        int finished = duration > 0 && position >= duration * finishThreshold ? 1 : 0;

        CourseLearnProgress progress = progressMapper.selectOne(new LambdaQueryWrapper<CourseLearnProgress>()
                .eq(CourseLearnProgress::getUserId, userId)
                .eq(CourseLearnProgress::getLessonId, req.lessonId()));
        LocalDateTime now = LocalDateTime.now();
        if (progress == null) {
            progress = new CourseLearnProgress();
            progress.setUserId(userId);
            progress.setLessonId(req.lessonId());
            progress.setPositionSeconds(position);
            progress.setDurationSeconds(duration);
            progress.setFinished(finished);
            progress.setLastLearnTime(now);
            progressMapper.insert(progress);
        } else {
            progress.setPositionSeconds(position);
            progress.setDurationSeconds(duration);
            progress.setFinished(finished);
            progress.setLastLearnTime(now);
            progressMapper.updateById(progress);
        }
    }

    /** 我的课程（续播入口）：按最近学习时间倒序，去重按课程 */
    public List<MyCourseVO> myCourses(Long userId) {
        List<CourseLearnProgress> progressList = progressMapper.selectList(
                new LambdaQueryWrapper<CourseLearnProgress>()
                        .eq(CourseLearnProgress::getUserId, userId)
                        .orderByDesc(CourseLearnProgress::getLastLearnTime));
        if (progressList.isEmpty()) {
            return List.of();
        }
        List<Long> lessonIds = progressList.stream().map(CourseLearnProgress::getLessonId).distinct().toList();
        Map<Long, CourseLesson> lessons = lessonMapper.selectBatchIds(lessonIds).stream()
                .collect(java.util.stream.Collectors.toMap(CourseLesson::getId, l -> l, (a, b) -> a));
        List<Long> chapterIds = lessons.values().stream().map(CourseLesson::getChapterId).distinct().toList();
        Map<Long, CourseChapter> chapters = chapterIds.isEmpty() ? Map.of()
                : chapterMapper.selectBatchIds(chapterIds).stream()
                        .collect(java.util.stream.Collectors.toMap(CourseChapter::getId, c -> c, (a, b) -> a));
        List<Long> courseIds = chapters.values().stream().map(CourseChapter::getCourseId).distinct().toList();
        Map<Long, Course> courses = courseIds.isEmpty() ? Map.of()
                : courseMapper.selectBatchIds(courseIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Course::getId, c -> c, (a, b) -> a));

        Map<Long, MyCourseVO> byCourse = new LinkedHashMap<>();
        for (CourseLearnProgress p : progressList) {
            CourseLesson lesson = lessons.get(p.getLessonId());
            if (lesson == null) {
                continue;
            }
            CourseChapter chapter = chapters.get(lesson.getChapterId());
            if (chapter == null) {
                continue;
            }
            Course course = courses.get(chapter.getCourseId());
            if (course == null || CourseStatus.of(course.getStatus()) != CourseStatus.PUBLISHED) {
                continue;
            }
            byCourse.putIfAbsent(course.getId(), new MyCourseVO(course.getId(), course.getTitle(),
                    course.getDescription(), lesson.getId(), lesson.getTitle(),
                    p.getPositionSeconds(), p.getLastLearnTime()));
        }
        return List.copyOf(byCourse.values());
    }
}
