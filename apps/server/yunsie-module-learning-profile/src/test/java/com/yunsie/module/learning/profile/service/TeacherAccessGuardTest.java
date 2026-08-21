package com.yunsie.module.learning.profile.service;

import com.yunsie.common.exception.BizException;
import com.yunsie.module.course.api.CourseQueryApi;
import com.yunsie.module.sys.api.SysAuthApi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 教师数据范围守卫单测：hasAllData 覆盖 / 自己课程通过 / 跨课程拒绝 / 学员课程交集。
 */
class TeacherAccessGuardTest {

    private final SysAuthApi sysAuthApi = mock(SysAuthApi.class);
    private final CourseQueryApi courseApi = mock(CourseQueryApi.class);

    private TeacherAccessGuard guard() {
        return new TeacherAccessGuard(sysAuthApi, courseApi);
    }

    @Test
    void admin_hasAllData_bypasses() {
        when(sysAuthApi.hasAllData(1L)).thenReturn(true);
        assertDoesNotThrow(() -> guard().requireCourseScope(1L, 99L));
        assertDoesNotThrow(() -> guard().requireStudentScope(1L, 55L));
    }

    @Test
    void teacher_ownCourse_passes() {
        when(sysAuthApi.hasAllData(2L)).thenReturn(false);
        when(courseApi.listTeacherCourseIds(2L)).thenReturn(List.of(7L, 8L));
        assertDoesNotThrow(() -> guard().requireCourseScope(2L, 7L));
    }

    @Test
    void teacher_otherCourse_rejected() {
        when(sysAuthApi.hasAllData(2L)).thenReturn(false);
        when(courseApi.listTeacherCourseIds(2L)).thenReturn(List.of(7L));
        BizException e = assertThrows(BizException.class, () -> guard().requireCourseScope(2L, 8L));
        assertEquals(30602, e.getCode());
    }

    @Test
    void studentScope_intersectionVisible() {
        when(sysAuthApi.hasAllData(2L)).thenReturn(false);
        when(courseApi.listTeacherCourseIds(2L)).thenReturn(List.of(7L));
        when(courseApi.listLearnerCourseIds(55L)).thenReturn(List.of(7L, 9L));
        assertDoesNotThrow(() -> guard().requireStudentScope(2L, 55L));
    }

    @Test
    void studentScope_noIntersection_rejected() {
        when(sysAuthApi.hasAllData(2L)).thenReturn(false);
        when(courseApi.listTeacherCourseIds(2L)).thenReturn(List.of(7L));
        when(courseApi.listLearnerCourseIds(55L)).thenReturn(List.of(9L));
        BizException e = assertThrows(BizException.class, () -> guard().requireStudentScope(2L, 55L));
        assertEquals(30602, e.getCode());
    }
}
