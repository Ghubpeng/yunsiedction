package com.yunsie.module.subject.controller;

import com.yunsie.common.api.Result;
import com.yunsie.module.subject.dto.NodeCreateReq;
import com.yunsie.module.subject.dto.NodeMoveReq;
import com.yunsie.module.subject.dto.NodeUpdateReq;
import com.yunsie.module.subject.service.KnowledgeNodeService;
import com.yunsie.module.subject.vo.NodeVO;
import com.yunsie.module.subject.vo.SubjectTreeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识节点管理接口（/api/v1/subject/nodes，后台）。
 */
@RestController
@RequestMapping("/api/v1/subject/nodes")
@RequiredArgsConstructor
@Validated
public class KnowledgeNodeController {

    private final KnowledgeNodeService nodeService;

    @PostMapping
    @PreAuthorize("@perm.has('subject:create')")
    public Result<Long> create(@Valid @RequestBody NodeCreateReq req) {
        return Result.ok(nodeService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('subject:update')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody NodeUpdateReq req) {
        nodeService.update(id, req);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('subject:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        nodeService.delete(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/move")
    @PreAuthorize("@perm.has('subject:update')")
    public Result<Void> move(@PathVariable Long id, @Valid @RequestBody NodeMoveReq req) {
        nodeService.move(id, req);
        return Result.ok(null);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('subject:read')")
    public Result<NodeVO> detail(@PathVariable Long id) {
        return Result.ok(nodeService.get(id));
    }

    @GetMapping("/{id}/children")
    @PreAuthorize("@perm.has('subject:read')")
    public Result<List<NodeVO>> children(@PathVariable Long id) {
        return Result.ok(nodeService.children(id));
    }

    /** 科目视角完整树（考试科目 → 章节 → 知识点 → 子知识点） */
    @GetMapping("/tree")
    @PreAuthorize("@perm.has('subject:read')")
    public Result<List<SubjectTreeVO>> tree(@RequestParam Long versionId) {
        return Result.ok(nodeService.subjectTree(versionId));
    }
}
