package com.yunsie.module.learning.profile.service;

import com.yunsie.common.exception.BizException;
import com.yunsie.module.course.api.CourseQueryApi;
import com.yunsie.module.learning.profile.error.LearnErrorCode;
import com.yunsie.module.sys.api.SysAuthApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 教师档案访问守卫（learning-profile §6 数据访问权限）：
 * 教师仅可见"自己课程范围内的学员"；管理员经 SysAuthApi.hasAllData 覆盖。
 * 课程归属一律经 CourseQueryApi（禁止直连 course 表/Mapper，模块边界铁律）。
 */
@Component
@RequiredArgsConstructor
public class TeacherAccessGuard {

    private final SysAuthApi sysAuthApi;
    private final CourseQueryApi courseQueryApi;

    /** 校验操作者可见某课程下的学员档案；不可见 → LEARN_FORBIDDEN */
    public void requireCourseScope(Long operatorId, Long courseId) {
        if (sysAuthApi.hasAllData(operatorId)) {
            return;
        }
        if (courseId != null && courseQueryApi.listTeacherCourseIds(operatorId).contains(courseId)) {
            return;
        }
        throw new BizException(LearnErrorCode.LEARN_FORBIDDEN);
    }

    /** 校验操作者可见某学员档案（教师课程集合 ∩ 学员学习课程集合 非空） */
    public void requireStudentScope(Long operatorId, Long studentId) {
        if (sysAuthApi.hasAllData(operatorId)) {
            return;
        }
        Set<Long> myCourses = new HashSet<>(courseQueryApi.listTeacherCourseIds(operatorId));
        Set<Long> studentCourses = new HashSet<>(courseQueryApi.listLearnerCourseIds(studentId));
        boolean visible = myCourses.stream().anyMatch(studentCourses::contains);
        if (!visible) {
            throw new BizException(LearnErrorCode.LEARN_FORBIDDEN);
        }
    }
}
