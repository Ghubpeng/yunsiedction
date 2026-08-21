package com.yunsie.module.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.course.dto.LessonNodeIdsReq;
import com.yunsie.module.course.entity.Course;
import com.yunsie.module.course.entity.CourseChapter;
import com.yunsie.module.course.entity.CourseLesson;
import com.yunsie.module.course.enums.CourseStatus;
import com.yunsie.module.course.error.CourseErrorCode;
import com.yunsie.module.course.mapper.CourseChapterMapper;
import com.yunsie.module.course.mapper.CourseLessonKnowledgeNodeMapper;
import com.yunsie.module.course.mapper.CourseLessonMapper;
import com.yunsie.module.course.mapper.CourseMapper;
import com.yunsie.module.course.service.CourseAccessGuard;
import com.yunsie.module.course.service.MinioStorageService;
import com.yunsie.module.subject.api.SubjectQueryApi;
import com.yunsie.module.sys.api.SysAuthApi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 小节服务单元测试：知识点关联校验（章节节点拒绝/未启用拒绝/跨证书拒绝/已发布课程编辑拒绝）。
 */
class CourseLessonServiceTest {

    private CourseLessonServiceImpl service(CourseLessonMapper lessonMapper, CourseChapterMapper chapterMapper,
                                            CourseMapper courseMapper, SysAuthApi sysAuthApi,
                                            SubjectQueryApi subjectQueryApi) {
        CourseAccessGuard guard = new CourseAccessGuard(courseMapper, sysAuthApi);
        return new CourseLessonServiceImpl(lessonMapper, chapterMapper,
                mock(CourseLessonKnowledgeNodeMapper.class), guard, subjectQueryApi,
                mock(MinioStorageService.class));
    }

    private void stubLessonChain(CourseLessonMapper lessonMapper, CourseChapterMapper chapterMapper,
                                 CourseMapper courseMapper, int courseStatus, long certId) {
        CourseLesson lesson = new CourseLesson();
        lesson.setId(5L);
        lesson.setChapterId(4L);
        lesson.setStatus(1);
        when(lessonMapper.selectById(5L)).thenReturn(lesson);
        CourseChapter chapter = new CourseChapter();
        chapter.setId(4L);
        chapter.setCourseId(3L);
        when(chapterMapper.selectById(4L)).thenReturn(chapter);
        Course course = new Course();
        course.setId(3L);
        course.setCertificateId(certId);
        course.setTeacherId(9L);
        course.setStatus(courseStatus);
        when(courseMapper.selectById(3L)).thenReturn(course);
    }

    private SysAuthApi allData() {
        SysAuthApi api = mock(SysAuthApi.class);
        when(api.hasAllData(9L)).thenReturn(true);
        return api;
    }

    @Test
    void nodeAssociation_chapterNode_rejected() {
        SubjectQueryApi subjectQueryApi = mock(SubjectQueryApi.class);
        when(subjectQueryApi.findNode(200L)).thenReturn(new SubjectQueryApi.KnowledgeNodeView(
                200L, 1L, 100L, 1L, 1, "章节", "CH1", "/1/", 1, 1));
        CourseLessonMapper lessonMapper = mock(CourseLessonMapper.class);
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        stubLessonChain(lessonMapper, chapterMapper, courseMapper, CourseStatus.DRAFT.code(), 100L);
        CourseLessonServiceImpl service = service(lessonMapper, chapterMapper, courseMapper, allData(), subjectQueryApi);

        BizException ex = assertThrows(BizException.class,
                () -> service.updateKnowledgeNodes(5L, new LessonNodeIdsReq(List.of(200L)), 9L));
        assertEquals(CourseErrorCode.NODE_ASSOC_INVALID.code(), ex.getCode());
    }

    @Test
    void nodeAssociation_disabledNode_rejected() {
        SubjectQueryApi subjectQueryApi = mock(SubjectQueryApi.class);
        when(subjectQueryApi.findNode(200L)).thenReturn(new SubjectQueryApi.KnowledgeNodeView(
                200L, 1L, 100L, 1L, 2, "知识点", "KP1", "/1/200/", 2, 0));
        CourseLessonMapper lessonMapper = mock(CourseLessonMapper.class);
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        stubLessonChain(lessonMapper, chapterMapper, courseMapper, CourseStatus.DRAFT.code(), 100L);

        BizException ex = assertThrows(BizException.class,
                () -> service(lessonMapper, chapterMapper, courseMapper, allData(), subjectQueryApi)
                        .updateKnowledgeNodes(5L, new LessonNodeIdsReq(List.of(200L)), 9L));
        assertEquals(CourseErrorCode.NODE_ASSOC_INVALID.code(), ex.getCode());
    }

    @Test
    void nodeAssociation_wrongCertificate_rejected() {
        SubjectQueryApi subjectQueryApi = mock(SubjectQueryApi.class);
        when(subjectQueryApi.findNode(200L)).thenReturn(new SubjectQueryApi.KnowledgeNodeView(
                200L, 1L, 999L, 1L, 2, "知识点", "KP1", "/1/200/", 2, 1));
        CourseLessonMapper lessonMapper = mock(CourseLessonMapper.class);
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        stubLessonChain(lessonMapper, chapterMapper, courseMapper, CourseStatus.DRAFT.code(), 100L);

        BizException ex = assertThrows(BizException.class,
                () -> service(lessonMapper, chapterMapper, courseMapper, allData(), subjectQueryApi)
                        .updateKnowledgeNodes(5L, new LessonNodeIdsReq(List.of(200L)), 9L));
        assertEquals(CourseErrorCode.NODE_ASSOC_INVALID.code(), ex.getCode());
    }

    @Test
    void publishedCourse_contentEdit_rejected_30503() {
        CourseLessonMapper lessonMapper = mock(CourseLessonMapper.class);
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        stubLessonChain(lessonMapper, chapterMapper, courseMapper, CourseStatus.PUBLISHED.code(), 100L);

        BizException ex = assertThrows(BizException.class,
                () -> service(lessonMapper, chapterMapper, courseMapper, allData(), mock(SubjectQueryApi.class))
                        .update(5L, new com.yunsie.module.course.dto.LessonUpdateReq("改名", 100, 0), 9L));
        assertEquals(CourseErrorCode.COURSE_PUBLISHED_NOT_EDITABLE.code(), ex.getCode());
    }
}
