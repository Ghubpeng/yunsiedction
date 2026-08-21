package com.yunsie.module.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.course.dto.CourseCreateReq;
import com.yunsie.module.course.entity.Course;
import com.yunsie.module.course.enums.CourseStatus;
import com.yunsie.module.course.error.CourseErrorCode;
import com.yunsie.module.course.mapper.CourseChapterMapper;
import com.yunsie.module.course.mapper.CourseLearnProgressMapper;
import com.yunsie.module.course.mapper.CourseLessonKnowledgeNodeMapper;
import com.yunsie.module.course.mapper.CourseLessonMapper;
import com.yunsie.module.course.mapper.CourseMapper;
import com.yunsie.module.course.service.CourseAccessGuard;
import com.yunsie.module.subject.api.SubjectQueryApi;
import com.yunsie.module.sys.api.SysAuthApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 课程服务单元测试：状态机 / 发布前置 / 教师数据范围。
 */
class CourseServiceTest {

    private CourseServiceImpl service(CourseMapper courseMapper, CourseChapterMapper chapterMapper,
                                      SysAuthApi sysAuthApi) {
        return service(courseMapper, chapterMapper, sysAuthApi, mock(SubjectQueryApi.class));
    }

    private CourseServiceImpl service(CourseMapper courseMapper, CourseChapterMapper chapterMapper,
                                      SysAuthApi sysAuthApi, SubjectQueryApi subjectQueryApi) {
        // 守卫与业务共享同一 courseMapper mock（requireCourse 依赖它）
        CourseAccessGuard guard = new CourseAccessGuard(courseMapper, sysAuthApi);
        return new CourseServiceImpl(courseMapper, chapterMapper, mock(CourseLessonMapper.class),
                mock(CourseLessonKnowledgeNodeMapper.class), mock(CourseLearnProgressMapper.class),
                mock(com.yunsie.module.course.mapper.CourseChapterPracticeMapper.class),
                guard, certApi(), subjectQueryApi);
    }

    private CertificateQueryApi certApi() {
        CertificateQueryApi api = mock(CertificateQueryApi.class);
        when(api.findCertificate(100L))
                .thenReturn(new CertificateQueryApi.CertificateView(100L, 1L, "护士执业资格", "nurse", "", 1));
        return api;
    }

    private Course course(Long id, int status, Long teacherId) {
        Course c = new Course();
        c.setId(id);
        c.setCertificateId(100L);
        c.setTeacherId(teacherId);
        c.setTitle("课程");
        c.setStatus(status);
        return c;
    }

    private SysAuthApi allData(Long userId) {
        SysAuthApi api = mock(SysAuthApi.class);
        when(api.hasAllData(userId)).thenReturn(true);
        return api;
    }

    private SysAuthApi scoped(Long userId) {
        SysAuthApi api = mock(SysAuthApi.class);
        when(api.hasAllData(userId)).thenReturn(false);
        return api;
    }

    @Test
    void publish_withoutEnabledContent_30505() {
        CourseMapper courseMapper = mock(CourseMapper.class);
        when(courseMapper.selectById(1L)).thenReturn(course(1L, CourseStatus.DRAFT.code(), 9L));
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        when(chapterMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        BizException ex = assertThrows(BizException.class,
                () -> service(courseMapper, chapterMapper, allData(9L)).publish(1L, 9L));
        assertEquals(CourseErrorCode.COURSE_PUBLISH_REQUIRE_CONTENT.code(), ex.getCode());
    }

    @Test
    void update_published_30503() {
        CourseMapper courseMapper = mock(CourseMapper.class);
        when(courseMapper.selectById(1L)).thenReturn(course(1L, CourseStatus.PUBLISHED.code(), 9L));

        BizException ex = assertThrows(BizException.class,
                () -> service(courseMapper, mock(CourseChapterMapper.class), allData(9L))
                        .update(1L, new com.yunsie.module.course.dto.CourseUpdateReq("改名", "", null, null, null), 9L));
        assertEquals(CourseErrorCode.COURSE_PUBLISHED_NOT_EDITABLE.code(), ex.getCode());
    }

    @Test
    void delete_published_30504() {
        CourseMapper courseMapper = mock(CourseMapper.class);
        when(courseMapper.selectById(1L)).thenReturn(course(1L, CourseStatus.PUBLISHED.code(), 9L));

        BizException ex = assertThrows(BizException.class,
                () -> service(courseMapper, mock(CourseChapterMapper.class), allData(9L)).delete(1L, 9L));
        assertEquals(CourseErrorCode.COURSE_PUBLISHED_NOT_DELETABLE.code(), ex.getCode());
    }

    @Test
    void teacher_manageOthersCourse_30506() {
        CourseMapper courseMapper = mock(CourseMapper.class);
        when(courseMapper.selectById(1L)).thenReturn(course(1L, CourseStatus.DRAFT.code(), 99L));

        BizException ex = assertThrows(BizException.class,
                () -> service(courseMapper, mock(CourseChapterMapper.class), scoped(9L))
                        .update(1L, new com.yunsie.module.course.dto.CourseUpdateReq("改名", "", null, null, null), 9L));
        assertEquals(CourseErrorCode.COURSE_FORBIDDEN.code(), ex.getCode());
    }

    @Test
    void teacher_createForOther_30506() {
        BizException ex = assertThrows(BizException.class,
                () -> service(mock(CourseMapper.class), mock(CourseChapterMapper.class), scoped(9L))
                        .create(new CourseCreateReq(100L, 99L, "课程", "", null, null, null), 9L));
        assertEquals(CourseErrorCode.COURSE_FORBIDDEN.code(), ex.getCode());
    }

    /** 章节归属合法（章节节点、启用、证书一致、科目一致）→ 课程落 chapterId */
    @Test
    void create_validChapter_setsChapterId() {
        CourseMapper courseMapper = mock(CourseMapper.class);
        SubjectQueryApi subjectApi = mock(SubjectQueryApi.class);
        when(subjectApi.findSubject(10L))
                .thenReturn(new SubjectQueryApi.SubjectView(10L, 100L, "内科护理学", "NK", 1));
        when(subjectApi.findNode(30L)).thenReturn(new SubjectQueryApi.KnowledgeNodeView(
                30L, 1L, 100L, 10L, 1, "第一章", "CH1", "/30/", 1, 1));
        doAnswer(inv -> {
            ((Course) inv.getArgument(0)).setId(7L);
            return 1;
        }).when(courseMapper).insert(any(Course.class));

        Long id = service(courseMapper, mock(CourseChapterMapper.class), allData(9L), subjectApi)
                .create(new CourseCreateReq(100L, 9L, "课程", "", 10L, null, 30L), 9L);
        assertEquals(7L, id);
        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseMapper).insert(captor.capture());
        assertEquals(30L, captor.getValue().getChapterId());
        assertEquals(10L, captor.getValue().getSubjectId());
    }

    /** 章节必须是章节节点（node_type=1）——知识点节点归属课程 → 30209 */
    @Test
    void create_chapterNotChapterNode_30209() {
        SubjectQueryApi subjectApi = mock(SubjectQueryApi.class);
        when(subjectApi.findSubject(10L))
                .thenReturn(new SubjectQueryApi.SubjectView(10L, 100L, "内科护理学", "NK", 1));
        when(subjectApi.findNode(30L)).thenReturn(new SubjectQueryApi.KnowledgeNodeView(
                30L, 1L, 100L, 10L, 2, "知识点", "KP1", "/30/", 2, 1));

        BizException ex = assertThrows(BizException.class,
                () -> service(mock(CourseMapper.class), mock(CourseChapterMapper.class), allData(9L), subjectApi)
                        .create(new CourseCreateReq(100L, 9L, "课程", "", 10L, null, 30L), 9L));
        assertEquals(30209, ex.getCode());
    }

    /** 章节与所选科目不一致（跨科目归属）→ 30209，禁止孤立课程 */
    @Test
    void create_chapterSubjectMismatch_30209() {
        SubjectQueryApi subjectApi = mock(SubjectQueryApi.class);
        when(subjectApi.findSubject(10L))
                .thenReturn(new SubjectQueryApi.SubjectView(10L, 100L, "内科护理学", "NK", 1));
        when(subjectApi.findNode(30L)).thenReturn(new SubjectQueryApi.KnowledgeNodeView(
                30L, 1L, 100L, 11L, 1, "第一章", "CH1", "/30/", 1, 1));

        BizException ex = assertThrows(BizException.class,
                () -> service(mock(CourseMapper.class), mock(CourseChapterMapper.class), allData(9L), subjectApi)
                        .create(new CourseCreateReq(100L, 9L, "课程", "", 10L, null, 30L), 9L));
        assertEquals(30209, ex.getCode());
    }

    /** 停用章节不可归属 → 30209 */
    @Test
    void create_disabledChapter_30209() {
        SubjectQueryApi subjectApi = mock(SubjectQueryApi.class);
        when(subjectApi.findSubject(10L))
                .thenReturn(new SubjectQueryApi.SubjectView(10L, 100L, "内科护理学", "NK", 1));
        when(subjectApi.findNode(30L)).thenReturn(new SubjectQueryApi.KnowledgeNodeView(
                30L, 1L, 100L, 10L, 1, "第一章", "CH1", "/30/", 1, 0));

        BizException ex = assertThrows(BizException.class,
                () -> service(mock(CourseMapper.class), mock(CourseChapterMapper.class), allData(9L), subjectApi)
                        .create(new CourseCreateReq(100L, 9L, "课程", "", 10L, null, 30L), 9L));
        assertEquals(30209, ex.getCode());
    }
}
