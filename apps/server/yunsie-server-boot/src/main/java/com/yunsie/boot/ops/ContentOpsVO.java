package com.yunsie.boot.ops;

import java.util.List;

/**
 * 内容运营看板 VO（Stage 2.3B）。
 */
public record ContentOpsVO(AssetStats assets, OpsReminders reminders) {

    public record AssetStats(long certificates, long courses, long videos, long questions) {
    }

    public record OpsReminders(long pendingReviewQuestions,
                               List<OpsEmptyChapterVO> emptyChapters,
                               List<OrphanChapterVO> orphanChapters) {
    }

    /** 空章节：课程章节下无任何小节 */
    public record OpsEmptyChapterVO(Long courseId, String courseTitle, Long chapterId, String chapterTitle) {
    }

    /** 无课程章节：知识体系章节未被任何课程归属 */
    public record OrphanChapterVO(Long nodeId, String nodeName, Long certificateId, String certificateName) {
    }
}
