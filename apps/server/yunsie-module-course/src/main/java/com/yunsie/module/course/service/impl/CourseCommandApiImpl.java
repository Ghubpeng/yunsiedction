package com.yunsie.module.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.course.api.CourseCommandApi;
import com.yunsie.module.course.entity.Course;
import com.yunsie.module.course.entity.CourseChapter;
import com.yunsie.module.course.entity.CourseLesson;
import com.yunsie.module.course.entity.CourseLessonKnowledgeNode;
import com.yunsie.module.course.enums.CourseStatus;
import com.yunsie.module.course.mapper.CourseChapterMapper;
import com.yunsie.module.course.mapper.CourseLessonKnowledgeNodeMapper;
import com.yunsie.module.course.mapper.CourseLessonMapper;
import com.yunsie.module.course.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程结构复制实现（Stage 2.3B）：章/节/知识点关联逐层复制；
 * 视频对象不复制（无媒体文件伪造）；目标课程状态=草稿（发布走课程状态机）。
 */
@Service
@RequiredArgsConstructor
public class CourseCommandApiImpl implements CourseCommandApi {

    private final CourseMapper courseMapper;
    private final CourseChapterMapper chapterMapper;
    private final CourseLessonMapper lessonMapper;
    private final CourseLessonKnowledgeNodeMapper lessonKnowledgeNodeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int copyCourseStructure(Long sourceCertId, Long targetCertId, Long targetVersionId,
                                   Map<Long, Long> subjectIdMap, Map<Long, Long> nodeIdMap) {
        List<Course> sourceCourses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getCertificateId, sourceCertId)
                .orderByAsc(Course::getId));
        int copied = 0;
        for (Course source : sourceCourses) {
            Course copy = new Course();
            copy.setCertificateId(targetCertId);
            copy.setSubjectId(source.getSubjectId() == null ? null : subjectIdMap.get(source.getSubjectId()));
            copy.setVersionId(targetVersionId);
            copy.setChapterId(source.getChapterId() == null ? null : nodeIdMap.get(source.getChapterId()));
            copy.setTeacherId(source.getTeacherId());
            copy.setTitle(source.getTitle());
            copy.setDescription(source.getDescription());
            copy.setType(source.getType());
            copy.setStatus(CourseStatus.DRAFT.code());
            courseMapper.insert(copy);
            copyChapters(source.getId(), copy.getId(), nodeIdMap);
            copied++;
        }
        return copied;
    }

    private void copyChapters(Long sourceCourseId, Long targetCourseId, Map<Long, Long> nodeIdMap) {
        List<CourseChapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<CourseChapter>()
                .eq(CourseChapter::getCourseId, sourceCourseId)
                .orderByAsc(CourseChapter::getSort).orderByAsc(CourseChapter::getId));
        Map<Long, Long> chapterMap = new HashMap<>();
        for (CourseChapter ch : chapters) {
            CourseChapter copy = new CourseChapter();
            copy.setCourseId(targetCourseId);
            copy.setTitle(ch.getTitle());
            copy.setSort(ch.getSort());
            copy.setStatus(ch.getStatus());
            chapterMapper.insert(copy);
            chapterMap.put(ch.getId(), copy.getId());
        }
        for (Map.Entry<Long, Long> e : chapterMap.entrySet()) {
            copyLessons(e.getKey(), e.getValue(), nodeIdMap);
        }
    }

    private void copyLessons(Long sourceChapterId, Long targetChapterId, Map<Long, Long> nodeIdMap) {
        List<CourseLesson> lessons = lessonMapper.selectList(new LambdaQueryWrapper<CourseLesson>()
                .eq(CourseLesson::getChapterId, sourceChapterId)
                .orderByAsc(CourseLesson::getSort).orderByAsc(CourseLesson::getId));
        for (CourseLesson lesson : lessons) {
            CourseLesson copy = new CourseLesson();
            copy.setChapterId(targetChapterId);
            copy.setTitle(lesson.getTitle());
            copy.setVideoKey(null); // 视频不复制：不伪造媒体对象（MinIO 文件未克隆）
            copy.setDurationSeconds(lesson.getDurationSeconds());
            copy.setSort(lesson.getSort());
            copy.setStatus(lesson.getStatus());
            lessonMapper.insert(copy);
            copyLessonNodes(lesson.getId(), copy.getId(), nodeIdMap);
        }
    }

    private void copyLessonNodes(Long sourceLessonId, Long targetLessonId, Map<Long, Long> nodeIdMap) {
        List<CourseLessonKnowledgeNode> links = lessonKnowledgeNodeMapper.selectList(
                new LambdaQueryWrapper<CourseLessonKnowledgeNode>()
                        .eq(CourseLessonKnowledgeNode::getLessonId, sourceLessonId));
        for (CourseLessonKnowledgeNode link : links) {
            Long mappedNodeId = nodeIdMap.get(link.getNodeId());
            if (mappedNodeId == null) {
                continue; // 知识点未复制 → 跳过该关联
            }
            CourseLessonKnowledgeNode copy = new CourseLessonKnowledgeNode();
            copy.setLessonId(targetLessonId);
            copy.setNodeId(mappedNodeId);
            lessonKnowledgeNodeMapper.insert(copy);
        }
    }
}
