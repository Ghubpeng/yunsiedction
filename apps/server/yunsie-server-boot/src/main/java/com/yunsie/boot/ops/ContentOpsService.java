package com.yunsie.boot.ops;

import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.course.api.CourseQueryApi;
import com.yunsie.module.question.api.QuestionQueryApi;
import com.yunsie.module.question.enums.QuestionStatus;
import com.yunsie.module.subject.api.SubjectQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 内容运营看板（boot 装配点，Stage 2.3B）：
 * 内容资产统计（证书/课程/视频/题目）+ 运营提醒（待审核题目/空章节/无课程章节）。
 * 全部经各域 QueryApi 聚合，只读、无跨域表访问。
 */
@Service
@RequiredArgsConstructor
public class ContentOpsService {

    /** 无课程章节扫描证书上限（防超大循环；演示/运营规模足够） */
    private static final int MAX_SCAN_CERTS = 50;
    private static final int MAX_REMINDER_ITEMS = 5;

    private final CertificateQueryApi certificateQueryApi;
    private final CourseQueryApi courseQueryApi;
    private final QuestionQueryApi questionQueryApi;
    private final SubjectQueryApi subjectQueryApi;

    public ContentOpsVO snapshot() {
        ContentOpsVO.AssetStats assets = new ContentOpsVO.AssetStats(
                certificateQueryApi.countAll(),
                courseQueryApi.countAll(),
                courseQueryApi.countLessonsWithVideo(),
                questionQueryApi.countAll());
        long pendingReview = questionQueryApi.countByStatus(QuestionStatus.PENDING_REVIEW.code());
        List<ContentOpsVO.OpsEmptyChapterVO> emptyChapters = courseQueryApi.listEmptyChapters(MAX_REMINDER_ITEMS).stream()
                .map(e -> new ContentOpsVO.OpsEmptyChapterVO(e.courseId(), e.courseTitle(), e.chapterId(), e.chapterTitle()))
                .toList();
        List<ContentOpsVO.OrphanChapterVO> orphanChapters = orphanChapters(MAX_REMINDER_ITEMS);
        return new ContentOpsVO(assets, new ContentOpsVO.OpsReminders(pendingReview, emptyChapters, orphanChapters));
    }

    /** 无课程章节：启用证书当前版本下、未被任何课程归属（course_course.chapter_id）的章节节点 */
    private List<ContentOpsVO.OrphanChapterVO> orphanChapters(int limit) {
        Set<Long> used = new HashSet<>(courseQueryApi.listUsedChapterIds());
        List<ContentOpsVO.OrphanChapterVO> result = new ArrayList<>();
        int scanned = 0;
        for (CertificateQueryApi.CertificateView cert : certificateQueryApi.listEnabled()) {
            if (result.size() >= limit || scanned >= MAX_SCAN_CERTS) {
                break;
            }
            scanned++;
            for (SubjectQueryApi.KnowledgeNodeView chapter : subjectQueryApi.listCurrentVersionChapterNodes(cert.id())) {
                if (result.size() >= limit) {
                    break;
                }
                if (!used.contains(chapter.id())) {
                    result.add(new ContentOpsVO.OrphanChapterVO(chapter.id(), chapter.name(), cert.id(), cert.name()));
                }
            }
        }
        return result;
    }
}
