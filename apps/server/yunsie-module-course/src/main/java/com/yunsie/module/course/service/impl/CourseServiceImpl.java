package com.yunsie.module.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunsie.common.api.PageResult;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.certificate.error.CertErrorCode;
import com.yunsie.module.subject.api.SubjectQueryApi;
import com.yunsie.module.subject.error.SubjectErrorCode;
import com.yunsie.module.course.dto.CourseCreateReq;
import com.yunsie.module.course.dto.CourseUpdateReq;
import com.yunsie.module.course.entity.Course;
import com.yunsie.module.course.entity.CourseChapter;
import com.yunsie.module.course.entity.CourseChapterPractice;
import com.yunsie.module.course.entity.CourseLearnProgress;
import com.yunsie.module.course.entity.CourseLesson;
import com.yunsie.module.course.entity.CourseLessonKnowledgeNode;
import com.yunsie.module.course.enums.CourseStatus;
import com.yunsie.module.course.error.CourseErrorCode;
import com.yunsie.module.course.mapper.CourseChapterMapper;
import com.yunsie.module.course.mapper.CourseChapterPracticeMapper;
import com.yunsie.module.course.mapper.CourseLearnProgressMapper;
import com.yunsie.module.course.mapper.CourseLessonKnowledgeNodeMapper;
import com.yunsie.module.course.mapper.CourseLessonMapper;
import com.yunsie.module.course.mapper.CourseMapper;
import com.yunsie.module.course.service.CourseAccessGuard;
import com.yunsie.module.course.service.CourseService;
import com.yunsie.module.course.vo.ChapterVO;
import com.yunsie.module.course.vo.CourseTreeVO;
import com.yunsie.module.course.vo.CourseVO;
import com.yunsie.module.course.vo.LessonVO;
import com.yunsie.module.course.vo.PublicCourseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 课程管理实现。
 * 状态机：草稿→已发布→已下架；已下架→草稿；发布前置校验；教师数据范围经 CourseAccessGuard。
 */
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;
    private final CourseChapterMapper chapterMapper;
    private final CourseLessonMapper lessonMapper;
    private final CourseLessonKnowledgeNodeMapper nodeMapper;
    private final CourseLearnProgressMapper progressMapper;
    private final CourseChapterPracticeMapper practiceMapper;
    private final CourseAccessGuard guard;
    private final CertificateQueryApi certificateQueryApi;
    private final SubjectQueryApi subjectQueryApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CourseCreateReq req, Long currentUserId) {
        requireCertificate(req.certificateId());
        if (!guard.hasAllData(currentUserId) && !req.teacherId().equals(currentUserId)) {
            throw new BizException(CourseErrorCode.COURSE_FORBIDDEN);
        }
        Course course = new Course();
        course.setCertificateId(req.certificateId());
        course.setSubjectId(resolveSubject(req.certificateId(), req.subjectId()));
        course.setVersionId(resolveVersion(req.certificateId(), req.versionId()));
        course.setChapterId(resolveChapter(req.certificateId(), course.getSubjectId(), req.chapterId()));
        course.setTeacherId(req.teacherId());
        course.setTitle(req.title());
        course.setDescription(req.description() == null ? "" : req.description());
        course.setType(1); // 录播；直播为预留扩展位
        course.setStatus(CourseStatus.DRAFT.code());
        courseMapper.insert(course);
        return course.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CourseUpdateReq req, Long currentUserId) {
        Course course = guard.requireCourse(id);
        guard.checkManage(course, currentUserId);
        guard.checkEditable(course);
        course.setTitle(req.title());
        course.setDescription(req.description());
        if (req.subjectId() != null) {
            course.setSubjectId(resolveSubject(course.getCertificateId(), req.subjectId()));
        }
        if (req.versionId() != null) {
            course.setVersionId(req.versionId());
        }
        if (req.chapterId() != null) {
            course.setChapterId(resolveChapter(course.getCertificateId(), course.getSubjectId(), req.chapterId()));
        }
        courseMapper.updateById(course);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long currentUserId) {
        Course course = guard.requireCourse(id);
        guard.checkManage(course, currentUserId);
        guard.checkDeletable(course);
        List<Long> lessonIds = lessonMapper.selectList(new LambdaQueryWrapper<CourseLesson>()
                        .eq(CourseLesson::getChapterId, courseChaptersIds(id)))
                .stream().map(CourseLesson::getId).toList();
        if (!lessonIds.isEmpty()) {
            nodeMapper.delete(new LambdaQueryWrapper<CourseLessonKnowledgeNode>()
                    .in(CourseLessonKnowledgeNode::getLessonId, lessonIds));
            lessonMapper.delete(new LambdaQueryWrapper<CourseLesson>().in(CourseLesson::getId, lessonIds));
        }
        chapterMapper.delete(new LambdaQueryWrapper<CourseChapter>().eq(CourseChapter::getCourseId, id));
        // 学习进度保留（历史用户数据；随课程 join 自动过滤）
        courseMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, Long currentUserId) {
        Course course = guard.requireCourse(id);
        guard.checkManage(course, currentUserId);
        CourseStatus status = CourseStatus.of(course.getStatus());
        if (status != CourseStatus.DRAFT && status != CourseStatus.OFFLINE) {
            throw new BizException(CourseErrorCode.COURSE_STATUS_INVALID_ACTION);
        }
        long enabledChapters = chapterMapper.selectCount(new LambdaQueryWrapper<CourseChapter>()
                .eq(CourseChapter::getCourseId, id).eq(CourseChapter::getStatus, 1));
        List<Long> chapterIds = chapterMapper.selectList(new LambdaQueryWrapper<CourseChapter>()
                        .eq(CourseChapter::getCourseId, id))
                .stream().map(CourseChapter::getId).toList();
        long enabledLessons = chapterIds.isEmpty() ? 0 : lessonMapper.selectCount(new LambdaQueryWrapper<CourseLesson>()
                .in(CourseLesson::getChapterId, chapterIds).eq(CourseLesson::getStatus, 1));
        if (enabledChapters == 0 || enabledLessons == 0) {
            throw new BizException(CourseErrorCode.COURSE_PUBLISH_REQUIRE_CONTENT);
        }
        course.setStatus(CourseStatus.PUBLISHED.code());
        courseMapper.updateById(course);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpublish(Long id, Long currentUserId) {
        Course course = guard.requireCourse(id);
        guard.checkManage(course, currentUserId);
        if (CourseStatus.of(course.getStatus()) != CourseStatus.PUBLISHED) {
            throw new BizException(CourseErrorCode.COURSE_STATUS_INVALID_ACTION);
        }
        course.setStatus(CourseStatus.OFFLINE.code());
        courseMapper.updateById(course);
    }

    @Override
    public CourseVO detail(Long id, Long currentUserId) {
        Course course = guard.requireCourse(id);
        guard.checkManage(course, currentUserId);
        return toVO(course);
    }

    @Override
    public PageResult<CourseVO> page(Long currentUserId, int pageNum, int pageSize,
                                     Long certificateId, Long subjectId, Integer status, String keyword) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (!guard.hasAllData(currentUserId)) {
            wrapper.eq(Course::getTeacherId, currentUserId); // 教师数据范围
        }
        if (certificateId != null) {
            wrapper.eq(Course::getCertificateId, certificateId);
        }
        if (subjectId != null) {
            wrapper.eq(Course::getSubjectId, subjectId);
        }
        if (status != null) {
            wrapper.eq(Course::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Course::getTitle, keyword);
        }
        wrapper.orderByDesc(Course::getId);
        Page<Course> page = courseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
    }

    @Override
    public PageResult<PublicCourseVO> publicPage(int pageNum, int pageSize, Long certificateId, Long subjectId,
                                                 String keyword) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<Course>()
                .eq(Course::getStatus, CourseStatus.PUBLISHED.code());
        if (certificateId != null) {
            wrapper.eq(Course::getCertificateId, certificateId);
        }
        if (subjectId != null) {
            wrapper.eq(Course::getSubjectId, subjectId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Course::getTitle, keyword);
        }
        wrapper.orderByDesc(Course::getId);
        Page<Course> page = courseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<PublicCourseVO> list = page.getRecords().stream()
                .map(c -> new PublicCourseVO(c.getId(), c.getTitle(), c.getDescription(),
                        chapterCount(c.getId()), lessonCount(c.getId())))
                .toList();
        return PageResult.of(list, page.getTotal());
    }

    @Override
    public CourseTreeVO publicTree(Long courseId, Long userId) {
        Course course = guard.requireCourse(courseId);
        if (CourseStatus.of(course.getStatus()) != CourseStatus.PUBLISHED) {
            throw new BizException(CourseErrorCode.COURSE_NOT_PUBLISHED);
        }
        List<CourseChapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<CourseChapter>()
                .eq(CourseChapter::getCourseId, courseId)
                .eq(CourseChapter::getStatus, 1)
                .orderByAsc(CourseChapter::getSort).orderByAsc(CourseChapter::getId));
        List<Long> chapterIds = chapters.stream().map(CourseChapter::getId).toList();
        List<CourseLesson> lessons = chapterIds.isEmpty() ? List.of()
                : lessonMapper.selectList(new LambdaQueryWrapper<CourseLesson>()
                        .in(CourseLesson::getChapterId, chapterIds)
                        .eq(CourseLesson::getStatus, 1)
                        .orderByAsc(CourseLesson::getSort).orderByAsc(CourseLesson::getId));
        List<Long> lessonIds = lessons.stream().map(CourseLesson::getId).toList();
        Map<Long, CourseLearnProgress> progressByLesson = lessonIds.isEmpty() || userId == null ? Map.of()
                : progressMapper.selectList(new LambdaQueryWrapper<CourseLearnProgress>()
                        .eq(CourseLearnProgress::getUserId, userId)
                        .in(CourseLearnProgress::getLessonId, lessonIds))
                        .stream().collect(Collectors.toMap(CourseLearnProgress::getLessonId, Function.identity(),
                                (a, b) -> a));
        Map<Long, List<CourseLesson>> lessonsByChapter = lessons.stream()
                .collect(Collectors.groupingBy(CourseLesson::getChapterId));
        // Stage 2.4：章节练习完成记录（掌握=视频完成+练习完成）
        Map<Long, CourseChapterPractice> practiceByChapter = chapterIds.isEmpty() || userId == null ? Map.of()
                : practiceMapper.selectList(new LambdaQueryWrapper<CourseChapterPractice>()
                        .eq(CourseChapterPractice::getUserId, userId)
                        .in(CourseChapterPractice::getChapterId, chapterIds))
                        .stream().collect(Collectors.toMap(CourseChapterPractice::getChapterId,
                                Function.identity(), (a, b) -> a));
        List<ChapterVO> chapterVOs = chapters.stream()
                .map(ch -> {
                    List<LessonVO> lessonVOs = lessonsByChapter.getOrDefault(ch.getId(), List.of()).stream()
                            .map(le -> {
                                CourseLearnProgress p = progressByLesson.get(le.getId());
                                return new LessonVO(le.getId(), le.getTitle(), le.getDurationSeconds(),
                                        le.getSort(), le.getStatus(),
                                        p == null ? 0 : p.getPositionSeconds(),
                                        p == null ? 0 : p.getFinished());
                            })
                            .toList();
                    // 章节完成：小节非空且全部 finished=1 才为 1
                    int finished = (!lessonVOs.isEmpty()
                            && lessonVOs.stream().allMatch(le -> le.finished() != null && le.finished() == 1)) ? 1 : 0;
                    CourseChapterPractice practice = practiceByChapter.get(ch.getId());
                    return new ChapterVO(ch.getId(), ch.getTitle(), ch.getSort(), ch.getStatus(), lessonVOs, finished,
                            practice == null ? 0 : (practice.getFinished() == null ? 0 : practice.getFinished()),
                            practice == null ? null : practice.getScore());
                })
                .toList();
        // 内容树归属名称（Stage 2.2：用户端展示 证书→科目→章节）
        CertificateQueryApi.CertificateView cert = certificateQueryApi.findCertificate(course.getCertificateId());
        SubjectQueryApi.SubjectView subject = course.getSubjectId() == null ? null
                : subjectQueryApi.findSubject(course.getSubjectId());
        return new CourseTreeVO(course.getId(), course.getTitle(), course.getDescription(),
                course.getSubjectId(), course.getVersionId(), course.getChapterId(),
                cert == null ? null : cert.name(),
                subject == null ? null : subject.name(),
                chapterName(course.getChapterId()),
                chapterVOs);
    }

    // ---------- helpers ----------

    private void requireCertificate(Long certificateId) {
        if (certificateQueryApi.findCertificate(certificateId) == null) {
            throw new BizException(CertErrorCode.CERT_NOT_FOUND);
        }
    }

    /** 校验科目存在、启用且属于该证书；非法抛 subject 域错误码（优先复用，不新增 course 码） */
    private Long resolveSubject(Long certificateId, Long subjectId) {
        if (subjectId == null) {
            return null;
        }
        SubjectQueryApi.SubjectView subject = subjectQueryApi.findSubject(subjectId);
        if (subject == null || subject.enabled() == null || subject.enabled() != 1
                || !certificateId.equals(subject.certificateId())) {
            throw new BizException(SubjectErrorCode.SUBJECT_NOT_FOUND);
        }
        return subjectId;
    }

    /** versionId 空且证书非空 → 回填证书当前版本（无当前版本返回 null，保持可空语义） */
    private Long resolveVersion(Long certificateId, Long versionId) {
        if (versionId != null) {
            return versionId;
        }
        if (certificateId == null) {
            return null;
        }
        return subjectQueryApi.findCurrentVersionId(certificateId);
    }

    /**
     * Stage 2.3A 学习章节归属校验：章节必须为 subject 域章节节点（node_type=1）、
     * 属于该证书，且与所选科目一致（课程不孤立于内容树）。空=未指定（保持可空语义）。
     */
    private Long resolveChapter(Long certificateId, Long subjectId, Long chapterNodeId) {
        if (chapterNodeId == null) {
            return null;
        }
        SubjectQueryApi.KnowledgeNodeView node = subjectQueryApi.findNode(chapterNodeId);
        if (node == null || node.enabled() == null || node.enabled() != 1
                || node.nodeType() == null || node.nodeType() != 1
                || !certificateId.equals(node.certificateId())
                || (subjectId != null && !subjectId.equals(node.subjectId()))) {
            throw new BizException(SubjectErrorCode.NODE_NOT_FOUND);
        }
        return chapterNodeId;
    }

    private List<Long> courseChaptersIds(Long courseId) {
        return chapterMapper.selectList(new LambdaQueryWrapper<CourseChapter>()
                        .eq(CourseChapter::getCourseId, courseId))
                .stream().map(CourseChapter::getId).toList();
    }

    private int chapterCount(Long courseId) {
        Long count = chapterMapper.selectCount(new LambdaQueryWrapper<CourseChapter>()
                .eq(CourseChapter::getCourseId, courseId));
        return count == null ? 0 : count.intValue();
    }

    private int lessonCount(Long courseId) {
        List<Long> chapterIds = courseChaptersIds(courseId);
        if (chapterIds.isEmpty()) {
            return 0;
        }
        Long count = lessonMapper.selectCount(new LambdaQueryWrapper<CourseLesson>()
                .in(CourseLesson::getChapterId, chapterIds));
        return count == null ? 0 : count.intValue();
    }

    private CourseVO toVO(Course course) {
        return new CourseVO(course.getId(), course.getCertificateId(), course.getSubjectId(),
                course.getVersionId(), course.getChapterId(), chapterName(course.getChapterId()),
                course.getTeacherId(),
                course.getTitle(), course.getDescription(), course.getType(), course.getStatus(),
                chapterCount(course.getId()), lessonCount(course.getId()));
    }

    /** 章节节点名称（chapterId 为空或节点已删时返回 null，展示端兼容） */
    private String chapterName(Long chapterId) {
        if (chapterId == null) {
            return null;
        }
        SubjectQueryApi.KnowledgeNodeView node = subjectQueryApi.findNode(chapterId);
        return node == null ? null : node.name();
    }
}
