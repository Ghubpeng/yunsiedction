package com.yunsie.boot.ops;

/**
 * 复制证书体系结果（Stage 2.3B）。
 */
public record CopySummaryVO(
        Long targetCertificateId,
        int subjectCount,
        int nodeCount,
        int courseCount,
        int questionCount) {
}
