package com.yunsie.module.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.course.entity.Course;
import com.yunsie.module.course.entity.CourseChapter;
import com.yunsie.module.course.entity.CourseChapterPractice;
import com.yunsie.module.course.entity.CourseLearnProgress;
import com.yunsie.module.course.entity.CourseLesson;
import com.yunsie.module.course.enums.CourseStatus;
import com.yunsie.module.course.mapper.CourseChapterMapper;
import com.yunsie.module.course.mapper.CourseChapterPracticeMapper;
import com.yunsie.module.course.mapper.CourseLearnProgressMapper;
import com.yunsie.module.course.mapper.CourseLessonMapper;
import com.yunsie.module.course.mapper.CourseMapper;
import com.yunsie.module.course.vo.LearningPathVO;
import com.yunsie.module.subject.api.SubjectQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学习路径服务（Stage 2.4）：以考试目标（证书）驱动的课程学习路径。
 * 章节四态（服务端计算，前端不复制规则）：
 *   mastered = 视频全部完成 + 章节练习已完成
 *   done     = 视频全部完成（尚未练习）
 *   learning = 有任意课时进度但未全部完成
 *   todo     = 未开始
 * 总完成度 = 视频完成章节数 / 总章节数；学习阶段按完成度分档（规则化，非 AI）。
 */
@Service
@RequiredArgsConstructor
public class LearningPathServiceImpl {

    private final CourseMapper courseMapper;
    private final CourseChapterMapper chapterMapper;
    private final CourseLessonMapper lessonMapper;
    private final CourseLearnProgressMapper progressMapper;
    private final CourseChapterPracticeMapper practiceMapper;
    private final SubjectQueryApi subjectQueryApi;
    private final CertificateQueryApi certificateQueryApi;

    public LearningPathVO path(Long userId, Long certificateId) {
        CertificateQueryApi.CertificateView cert = certificateId == null ? null
                : certificateQueryApi.findCertificate(certificateId);
        List<Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getStatus, CourseStatus.PUBLISHED.code())
                .eq(certificateId != null, Course::getCertificateId, certificateId)
                .orderByAsc(Course::getId));

        // 进度与章节练习（本人；批量加载避免 N+1）
        Map<Long, CourseLearnProgress> progressByLesson = userId == null ? Map.of()
                : progressMapper.selectList(new LambdaQueryWrapper<CourseLearnProgress>()
                        .eq(CourseLearnProgress::getUserId, userId))
                        .stream().collect(Collectors.toMap(CourseLearnProgress::getLessonId,
                                Function.identity(), (a, b) -> a));
        Map<Long, CourseChapterPractice> practiceByChapter = userId == null ? Map.of()
                : practiceMapper.selectList(new LambdaQueryWrapper<CourseChapterPractice>()
                        .eq(CourseChapterPractice::getUserId, userId))
                        .stream().collect(Collectors.toMap(CourseChapterPractice::getChapterId,
                                Function.identity(), (a, b) -> a));

        int total = 0;
        int finished = 0;
        int mastered = 0;
        LearningPathVO.NextTaskVO nextTask = null;
        Map<Long, List<LearningPathVO.CourseBlockVO>> bySubject = new LinkedHashMap<>();

        for (Course course : courses) {
            List<CourseChapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<CourseChapter>()
                    .eq(CourseChapter::getCourseId, course.getId())
                    .eq(CourseChapter::getStatus, 1)
                    .orderByAsc(CourseChapter::getSort).orderByAsc(CourseChapter::getId));
            List<Long> chapterIds = chapters.stream().map(CourseChapter::getId).toList();
            List<CourseLesson> lessons = chapterIds.isEmpty() ? List.of()
                    : lessonMapper.selectList(new LambdaQueryWrapper<CourseLesson>()
                            .in(CourseLesson::getChapterId, chapterIds)
                            .eq(CourseLesson::getStatus, 1)
                            .orderByAsc(CourseLesson::getSort).orderByAsc(CourseLesson::getId));
            Map<Long, List<CourseLesson>> lessonsByChapter = lessons.stream()
                    .collect(Collectors.groupingBy(CourseLesson::getChapterId));

            List<LearningPathVO.ChapterBlockVO> chapterBlocks = new ArrayList<>();
            for (CourseChapter chapter : chapters) {
                List<CourseLesson> chLessons = lessonsByChapter.getOrDefault(chapter.getId(), List.of());
                boolean hasLessons = !chLessons.isEmpty();
                boolean allFinished = hasLessons && chLessons.stream().allMatch(
                        l -> progressByLesson.get(l.getId()) != null
                                && progressByLesson.get(l.getId()).getFinished() != null
                                && progressByLesson.get(l.getId()).getFinished() == 1);
                boolean anyProgress = chLessons.stream().anyMatch(l -> {
                    CourseLearnProgress p = progressByLesson.get(l.getId());
                    return p != null && ((p.getFinished() != null && p.getFinished() == 1)
                            || (p.getPositionSeconds() != null && p.getPositionSeconds() > 0));
                });
                CourseChapterPractice practice = practiceByChapter.get(chapter.getId());
                boolean practiceDone = practice != null && practice.getFinished() != null && practice.getFinished() == 1;
                String state = allFinished
                        ? (practiceDone ? "mastered" : "done")
                        : (anyProgress ? "learning" : "todo");
                total++;
                if (allFinished) {
                    finished++;
                }
                if (allFinished && practiceDone) {
                    mastered++;
                }
                chapterBlocks.add(new LearningPathVO.ChapterBlockVO(chapter.getId(), chapter.getTitle(), state,
                        practice == null ? null : practice.getScore()));

                // 下一学习任务：优先未完成课时的章节；其次视频完成但未练习的章节
                if (nextTask == null) {
                    if ("done".equals(state)) {
                        nextTask = new LearningPathVO.NextTaskVO(course.getId(), course.getTitle(),
                                chapter.getId(), chapter.getTitle(), null, null, "practice");
                    } else if ("todo".equals(state) || "learning".equals(state)) {
                        CourseLesson next = chLessons.stream()
                                .filter(l -> {
                                    CourseLearnProgress p = progressByLesson.get(l.getId());
                                    return p == null || p.getFinished() == null || p.getFinished() != 1;
                                })
                                .findFirst().orElse(null);
                        if (next != null) {
                            nextTask = new LearningPathVO.NextTaskVO(course.getId(), course.getTitle(),
                                    chapter.getId(), chapter.getTitle(), next.getId(), next.getTitle(), "lesson");
                        }
                    }
                }
            }

            bySubject.computeIfAbsent(course.getSubjectId() == null ? -1L : course.getSubjectId(),
                    k -> new ArrayList<>()).add(new LearningPathVO.CourseBlockVO(course.getId(), course.getTitle(),
                    chapterBlocks));
        }

        List<LearningPathVO.SubjectBlockVO> subjects = bySubject.entrySet().stream()
                .map(e -> {
                    Long subjectId = e.getKey() == -1L ? null : e.getKey();
                    String name = subjectId == null ? "未分科目"
                            : (subjectQueryApi.findSubject(subjectId) == null ? null
                                    : subjectQueryApi.findSubject(subjectId).name());
                    return new LearningPathVO.SubjectBlockVO(subjectId,
                            name == null ? ("科目#" + subjectId) : name, e.getValue());
                })
                .toList();

        int percent = total == 0 ? 0 : Math.round(100f * finished / total);
        String stage = percent < 25 ? "起步" : percent < 50 ? "基础" : percent < 80 ? "强化" : "冲刺";
        return new LearningPathVO(certificateId, cert == null ? null : cert.name(),
                total, finished, mastered, percent, stage, nextTask, subjects);
    }
}
