package com.yunsie.module.course.service;

import com.yunsie.common.api.PageResult;
import com.yunsie.module.course.dto.CourseCreateReq;
import com.yunsie.module.course.dto.CourseUpdateReq;
import com.yunsie.module.course.vo.CourseTreeVO;
import com.yunsie.module.course.vo.CourseVO;
import com.yunsie.module.course.vo.PublicCourseVO;

/**
 * 课程管理服务（定义/发布/下架/列表/用户端公开树）。
 */
public interface CourseService {

    Long create(CourseCreateReq req, Long currentUserId);

    /** 仅草稿/已下架可编辑 */
    void update(Long id, CourseUpdateReq req, Long currentUserId);

    /** 仅草稿/已下架可删；级联逻辑删除章节/小节/节点关联（进度保留，随 join 过滤） */
    void delete(Long id, Long currentUserId);

    /** 发布前置：至少 1 个启用章节 且 至少 1 个启用小节 */
    void publish(Long id, Long currentUserId);

    void unpublish(Long id, Long currentUserId);

    CourseVO detail(Long id, Long currentUserId);

    /** 管理端分页（教师数据范围：非全部数据仅本人课程；可选证书/科目过滤） */
    PageResult<CourseVO> page(Long currentUserId, int pageNum, int pageSize,
                              Long certificateId, Long subjectId, Integer status, String keyword);

    /** 用户端公开课程列表（仅已发布；可选证书/科目过滤） */
    PageResult<PublicCourseVO> publicPage(int pageNum, int pageSize, Long certificateId, Long subjectId,
                                          String keyword);

    /** 用户端课程树（仅已发布；禁用节点隐藏；含本人进度） */
    CourseTreeVO publicTree(Long courseId, Long userId);
}
