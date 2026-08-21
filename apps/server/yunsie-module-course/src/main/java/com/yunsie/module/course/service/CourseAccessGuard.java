package com.yunsie.module.course.service;

import com.yunsie.common.exception.BizException;
import com.yunsie.module.course.entity.Course;
import com.yunsie.module.course.enums.CourseStatus;
import com.yunsie.module.course.error.CourseErrorCode;
import com.yunsie.module.course.mapper.CourseMapper;
import com.yunsie.module.sys.api.SysAuthApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 课程访问守卫（course 域内部）：
 * - 教师数据范围：hasAllData=true 可管理全部课程；否则仅 course.teacherId == 当前用户（决策 14）。
 * - 状态保护：已发布禁止编辑/删除（决策 15）。
 */
@Component
@RequiredArgsConstructor
public class CourseAccessGuard {

    private final CourseMapper courseMapper;
    private final SysAuthApi sysAuthApi;

    public Course requireCourse(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BizException(CourseErrorCode.COURSE_NOT_FOUND);
        }
        return course;
    }

    /** 教师数据范围校验：全部数据 or 本人课程 */
    public void checkManage(Course course, Long currentUserId) {
        if (sysAuthApi.hasAllData(currentUserId)) {
            return;
        }
        if (course.getTeacherId() != null && course.getTeacherId().equals(currentUserId)) {
            return;
        }
        throw new BizException(CourseErrorCode.COURSE_FORBIDDEN);
    }

    /** 已发布禁止编辑（章节/小节增删改与课程编辑共用） */
    public void checkEditable(Course course) {
        if (CourseStatus.of(course.getStatus()) == CourseStatus.PUBLISHED) {
            throw new BizException(CourseErrorCode.COURSE_PUBLISHED_NOT_EDITABLE);
        }
    }

    /** 已发布禁止删除 */
    public void checkDeletable(Course course) {
        if (CourseStatus.of(course.getStatus()) == CourseStatus.PUBLISHED) {
            throw new BizException(CourseErrorCode.COURSE_PUBLISHED_NOT_DELETABLE);
        }
    }

    public boolean hasAllData(Long currentUserId) {
        return sysAuthApi.hasAllData(currentUserId);
    }
}
