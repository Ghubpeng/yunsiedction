package com.yunsie.module.subject.controller;

import com.yunsie.common.api.Result;
import com.yunsie.module.subject.service.ExamSubjectService;
import com.yunsie.module.subject.service.KnowledgeNodeService;
import com.yunsie.module.subject.vo.SubjectTreeVO;
import com.yunsie.module.subject.vo.SubjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户公开读取接口（/api/v1/subject/public/**）。
 * 登录即可访问；只返回：启用证书 + 启用科目 + 当前版本 + 启用节点。
 * 普通用户没有任何修改入口（DataScope：公共基础数据只读）。
 */
@RestController
@RequestMapping("/api/v1/subject/public")
@RequiredArgsConstructor
@Validated
public class SubjectPublicController {

    private final ExamSubjectService subjectService;
    private final KnowledgeNodeService nodeService;

    @GetMapping("/subjects")
    public Result<List<SubjectVO>> subjects(@RequestParam Long certificateId) {
        return Result.ok(subjectService.publicListByCertificate(certificateId));
    }

    @GetMapping("/tree")
    public Result<List<SubjectTreeVO>> tree(@RequestParam Long certificateId) {
        return Result.ok(nodeService.publicTree(certificateId));
    }
}
