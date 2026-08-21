package com.yunsie.module.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.course.dto.ChapterCreateReq;
import com.yunsie.module.course.dto.ChapterUpdateReq;
import com.yunsie.module.course.entity.Course;
import com.yunsie.module.course.entity.CourseChapter;
import com.yunsie.module.course.entity.CourseLesson;
import com.yunsie.module.course.error.CourseErrorCode;
import com.yunsie.module.course.mapper.CourseChapterMapper;
import com.yunsie.module.course.mapper.CourseLessonMapper;
import com.yunsie.module.course.service.CourseAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 章节服务：增删改受课程状态保护（已发布仅允许启用/禁用）；删除保护=存在小节禁止删除。
 */
@Service
@RequiredArgsConstructor
public class CourseChapterServiceImpl {

    private final CourseChapterMapper chapterMapper;
    private final CourseLessonMapper lessonMapper;
    private final CourseAccessGuard guard;

    @Transactional(rollbackFor = Exception.class)
    public Long create(ChapterCreateReq req, Long currentUserId) {
        Course course = guard.requireCourse(req.courseId());
        guard.checkManage(course, currentUserId);
        guard.checkEditable(course);
        CourseChapter chapter = new CourseChapter();
        chapter.setCourseId(req.courseId());
        chapter.setTitle(req.title());
        chapter.setSort(req.sort() == null ? 0 : req.sort());
        chapter.setStatus(1);
        chapterMapper.insert(chapter);
        return chapter.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ChapterUpdateReq req, Long currentUserId) {
        CourseChapter chapter = requireChapter(id);
        Course course = guard.requireCourse(chapter.getCourseId());
        guard.checkManage(course, currentUserId);
        guard.checkEditable(course);
        chapter.setTitle(req.title());
        chapter.setSort(req.sort());
        chapterMapper.updateById(chapter);
    }

    /** 启用/禁用：已发布课程也允许（决策 16） */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, int status, Long currentUserId) {
        CourseChapter chapter = requireChapter(id);
        Course course = guard.requireCourse(chapter.getCourseId());
        guard.checkManage(course, currentUserId);
        chapter.setStatus(status);
        chapterMapper.updateById(chapter);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long currentUserId) {
        CourseChapter chapter = requireChapter(id);
        Course course = guard.requireCourse(chapter.getCourseId());
        guard.checkManage(course, currentUserId);
        guard.checkEditable(course);
        Long lessons = lessonMapper.selectCount(new LambdaQueryWrapper<CourseLesson>()
                .eq(CourseLesson::getChapterId, id));
        if (lessons != null && lessons > 0) {
            throw new BizException(CourseErrorCode.CHAPTER_HAS_LESSONS);
        }
        chapterMapper.deleteById(id);
    }

    /** 管理端视图：全部章节+小节（含禁用） */
    public List<com.yunsie.module.course.vo.ChapterVO> adminTree(Long courseId, Long currentUserId) {
        Course course = guard.requireCourse(courseId);
        guard.checkManage(course, currentUserId);
        List<CourseChapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<CourseChapter>()
                .eq(CourseChapter::getCourseId, courseId)
                .orderByAsc(CourseChapter::getSort).orderByAsc(CourseChapter::getId));
        List<Long> chapterIds = chapters.stream().map(CourseChapter::getId).toList();
        List<CourseLesson> lessons = chapterIds.isEmpty() ? List.of()
                : lessonMapper.selectList(new LambdaQueryWrapper<CourseLesson>()
                        .in(CourseLesson::getChapterId, chapterIds)
                        .orderByAsc(CourseLesson::getSort).orderByAsc(CourseLesson::getId));
        var lessonsByChapter = lessons.stream()
                .collect(java.util.stream.Collectors.groupingBy(CourseLesson::getChapterId));
        return chapters.stream()
                .map(ch -> new com.yunsie.module.course.vo.ChapterVO(ch.getId(), ch.getTitle(), ch.getSort(),
                        ch.getStatus(),
                        lessonsByChapter.getOrDefault(ch.getId(), List.of()).stream()
                                .map(le -> new com.yunsie.module.course.vo.LessonVO(le.getId(), le.getTitle(),
                                        le.getDurationSeconds(), le.getSort(), le.getStatus(), 0, 0))
                                .toList(),
                        0, 0, null))
                .toList();
    }

    private CourseChapter requireChapter(Long id) {
        CourseChapter chapter = chapterMapper.selectById(id);
        if (chapter == null) {
            throw new BizException(CourseErrorCode.CHAPTER_NOT_FOUND);
        }
        return chapter;
    }
}
