package com.yunsie.module.learning.profile.controller;

import com.yunsie.common.api.Result;
import com.yunsie.common.security.CurrentUser;
import com.yunsie.module.learning.profile.service.TeacherProfileService;
import com.yunsie.module.learning.profile.vo.StudentListItemVO;
import com.yunsie.module.learning.profile.vo.StudentProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 教师/管理员学员档案接口（learn:profile:view）。
 * 教师仅可见自己课程范围内的学员（TeacherAccessGuard：课程归属 DataScope，hasAllData 覆盖）。
 */
@RestController
@RequestMapping("/api/v1/learn/students")
@RequiredArgsConstructor
public class LearnTeacherController {

    private final TeacherProfileService teacherProfileService;

    @PreAuthorize("@perm.has('learn:profile:view')")
    @GetMapping
    public Result<List<StudentListItemVO>> students(@CurrentUser Long operatorId,
                                                    @RequestParam Long courseId) {
        return Result.ok(teacherProfileService.studentsByCourse(operatorId, courseId));
    }

    @PreAuthorize("@perm.has('learn:profile:view')")
    @GetMapping("/{userId}")
    public Result<StudentProfileVO> student(@CurrentUser Long operatorId, @PathVariable Long userId) {
        return Result.ok(teacherProfileService.studentProfile(operatorId, userId));
    }
}
