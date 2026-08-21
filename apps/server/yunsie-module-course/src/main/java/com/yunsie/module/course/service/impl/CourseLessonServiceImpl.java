package com.yunsie.module.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.course.dto.LessonCreateReq;
import com.yunsie.module.course.dto.LessonNodeIdsReq;
import com.yunsie.module.course.dto.LessonUpdateReq;
import com.yunsie.module.course.entity.Course;
import com.yunsie.module.course.entity.CourseChapter;
import com.yunsie.module.course.entity.CourseLesson;
import com.yunsie.module.course.entity.CourseLessonKnowledgeNode;
import com.yunsie.module.course.error.CourseErrorCode;
import com.yunsie.module.course.mapper.CourseChapterMapper;
import com.yunsie.module.course.mapper.CourseLessonKnowledgeNodeMapper;
import com.yunsie.module.course.mapper.CourseLessonMapper;
import com.yunsie.module.course.service.CourseAccessGuard;
import com.yunsie.module.course.service.MinioStorageService;
import com.yunsie.module.subject.api.SubjectQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * 小节服务：增删改受课程状态保护（已发布仅允许启用/禁用）；
 * 知识点关联经 SubjectQueryApi 校验；视频上传至 MinIO。
 */
@Service
@RequiredArgsConstructor
public class CourseLessonServiceImpl {

    private static final long MAX_VIDEO_SIZE = 200L * 1024 * 1024;

    private final CourseLessonMapper lessonMapper;
    private final CourseChapterMapper chapterMapper;
    private final CourseLessonKnowledgeNodeMapper nodeMapper;
    private final CourseAccessGuard guard;
    private final SubjectQueryApi subjectQueryApi;
    private final MinioStorageService storageService;

    @Transactional(rollbackFor = Exception.class)
    public Long create(LessonCreateReq req, Long currentUserId) {
        CourseChapter chapter = requireChapter(req.chapterId());
        Course course = guard.requireCourse(chapter.getCourseId());
        guard.checkManage(course, currentUserId);
        guard.checkEditable(course);
        CourseLesson lesson = new CourseLesson();
        lesson.setChapterId(req.chapterId());
        lesson.setTitle(req.title());
        lesson.setDurationSeconds(req.durationSeconds() == null ? 0 : req.durationSeconds());
        lesson.setSort(req.sort() == null ? 0 : req.sort());
        lesson.setStatus(1);
        lessonMapper.insert(lesson);
        return lesson.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, LessonUpdateReq req, Long currentUserId) {
        CourseLesson lesson = requireLesson(id);
        Course course = requireCourseOf(lesson);
        guard.checkManage(course, currentUserId);
        guard.checkEditable(course);
        lesson.setTitle(req.title());
        lesson.setDurationSeconds(req.durationSeconds() == null ? 0 : req.durationSeconds());
        lesson.setSort(req.sort());
        lessonMapper.updateById(lesson);
    }

    /** 启用/禁用：已发布课程也允许（决策 16） */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, int status, Long currentUserId) {
        CourseLesson lesson = requireLesson(id);
        Course course = requireCourseOf(lesson);
        guard.checkManage(course, currentUserId);
        lesson.setStatus(status);
        lessonMapper.updateById(lesson);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long currentUserId) {
        CourseLesson lesson = requireLesson(id);
        Course course = requireCourseOf(lesson);
        guard.checkManage(course, currentUserId);
        guard.checkEditable(course);
        nodeMapper.delete(new LambdaQueryWrapper<CourseLessonKnowledgeNode>()
                .eq(CourseLessonKnowledgeNode::getLessonId, id));
        if (lesson.getVideoKey() != null) {
            storageService.removeQuietly(lesson.getVideoKey());
        }
        lessonMapper.deleteById(id);
    }

    /** 覆盖式知识点关联（可选；经 SubjectQueryApi 校验知识点/子知识点、启用、同证书） */
    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledgeNodes(Long id, LessonNodeIdsReq req, Long currentUserId) {
        CourseLesson lesson = requireLesson(id);
        Course course = requireCourseOf(lesson);
        guard.checkManage(course, currentUserId);
        guard.checkEditable(course);
        for (Long nodeId : new LinkedHashSet<>(req.nodeIds())) {
            SubjectQueryApi.KnowledgeNodeView node = subjectQueryApi.findNode(nodeId);
            if (node == null || node.enabled() == null || node.enabled() != 1
                    || (node.nodeType() != 2 && node.nodeType() != 3)
                    || !course.getCertificateId().equals(node.certificateId())) {
                throw new BizException(CourseErrorCode.NODE_ASSOC_INVALID);
            }
        }
        nodeMapper.delete(new LambdaQueryWrapper<CourseLessonKnowledgeNode>()
                .eq(CourseLessonKnowledgeNode::getLessonId, id));
        for (Long nodeId : new LinkedHashSet<>(req.nodeIds())) {
            CourseLessonKnowledgeNode link = new CourseLessonKnowledgeNode();
            link.setLessonId(id);
            link.setNodeId(nodeId);
            nodeMapper.insert(link);
        }
    }

    /** 视频上传（原文件直存 MinIO；已发布课程禁止上传/替换） */
    @Transactional(rollbackFor = Exception.class)
    public void uploadVideo(Long id, MultipartFile file, Long currentUserId) {
        CourseLesson lesson = requireLesson(id);
        Course course = requireCourseOf(lesson);
        guard.checkManage(course, currentUserId);
        guard.checkEditable(course);
        if (file == null || file.isEmpty() || file.getSize() > MAX_VIDEO_SIZE) {
            throw new BizException(CourseErrorCode.VIDEO_UPLOAD_FAILED);
        }
        String original = file.getOriginalFilename() == null ? "video" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
        String objectKey = "course/" + course.getId() + "/" + id + "/" + UUID.randomUUID() + ext;
        try {
            storageService.upload(objectKey, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new BizException(CourseErrorCode.VIDEO_UPLOAD_FAILED);
        }
        if (lesson.getVideoKey() != null && !lesson.getVideoKey().equals(objectKey)) {
            storageService.removeQuietly(lesson.getVideoKey());
        }
        lesson.setVideoKey(objectKey);
        lessonMapper.updateById(lesson);
    }

    public CourseLesson requireLesson(Long id) {
        CourseLesson lesson = lessonMapper.selectById(id);
        if (lesson == null) {
            throw new BizException(CourseErrorCode.LESSON_NOT_FOUND);
        }
        return lesson;
    }

    private CourseChapter requireChapter(Long id) {
        CourseChapter chapter = chapterMapper.selectById(id);
        if (chapter == null) {
            throw new BizException(CourseErrorCode.CHAPTER_NOT_FOUND);
        }
        return chapter;
    }

    private Course requireCourseOf(CourseLesson lesson) {
        CourseChapter chapter = requireChapter(lesson.getChapterId());
        return guard.requireCourse(chapter.getCourseId());
    }
}
