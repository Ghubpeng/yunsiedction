package com.yunsie.module.learning.profile.service;

import com.yunsie.common.exception.BizException;
import com.yunsie.module.course.api.CourseQueryApi;
import com.yunsie.module.learning.profile.error.LearnErrorCode;
import com.yunsie.module.learning.profile.vo.StudentListItemVO;
import com.yunsie.module.learning.profile.vo.StudentProfileVO;
import com.yunsie.module.user.api.UserAuthApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 教师/管理员学员档案视图服务。
 * 访问控制：教师经 TeacherAccessGuard（课程归属 DataScope，hasAllData 覆盖）；
 * 学员名单与归属一律经 CourseQueryApi（禁止直连 course 表）。
 */
@Service
@RequiredArgsConstructor
public class TeacherProfileService {

    private final TeacherAccessGuard accessGuard;
    private final ProfileSyncService syncService;
    private final ProfileQueryService queryService;
    private final WeaknessService weaknessService;
    private final CourseQueryApi courseQueryApi;
    private final UserAuthApi userAuthApi;

    /** 某课程下的学员档案列表（教师数据范围） */
    public List<StudentListItemVO> studentsByCourse(Long operatorId, Long courseId) {
        accessGuard.requireCourseScope(operatorId, courseId);
        List<Long> learnerIds = courseQueryApi.listLearnerIdsByCourse(courseId);
        return learnerIds.stream()
                .map(this::toListItem)
                .toList();
    }

    /** 单个学员完整档案（教师数据范围：课程集合交集非空） */
    public StudentProfileVO studentProfile(Long operatorId, Long studentId) {
        requireStudentExists(studentId);
        accessGuard.requireStudentScope(operatorId, studentId);
        syncService.sync(studentId);
        UserAuthApi.UserAuthView user = userAuthApi.findById(studentId);
        return new StudentProfileVO(studentId,
                user == null ? null : user.nickname(),
                queryService.summaryOf(studentId),
                queryService.masteryOf(studentId),
                weaknessService.weaknessOf(studentId, null));
    }

    private StudentListItemVO toListItem(Long learnerId) {
        syncService.sync(learnerId);
        UserAuthApi.UserAuthView user = userAuthApi.findById(learnerId);
        return new StudentListItemVO(learnerId,
                user == null ? null : user.nickname(),
                queryService.summaryOf(learnerId));
    }

    private void requireStudentExists(Long studentId) {
        if (userAuthApi.findById(studentId) == null) {
            throw new BizException(LearnErrorCode.STUDENT_NOT_FOUND);
        }
    }
}
