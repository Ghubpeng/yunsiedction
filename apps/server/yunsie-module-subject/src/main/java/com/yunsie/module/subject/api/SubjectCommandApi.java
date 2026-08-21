package com.yunsie.module.subject.api;

import java.util.Map;

/**
 * 知识体系写入契约（subject 域对外 API，Stage 2.3B 内容复制）。
 * 复制源证书当前版本的知识体系（版本→科目→章节/知识点）到目标证书（目标证书已存在）。
 * 由 boot（装配点）编排调用；表访问全部留在 subject 域内。
 */
public interface SubjectCommandApi {

    /**
     * 复制知识体系。源证书无当前版本时返回零结果（newVersionId=null，不抛错）。
     *
     * @param sourceCertId 源证书
     * @param targetCertId 目标证书（需已创建）
     */
    CopyResult copySystem(Long sourceCertId, Long targetCertId);

    record CopyResult(int subjectCount, int nodeCount, Long newVersionId,
                      Map<Long, Long> subjectIdMap, Map<Long, Long> nodeIdMap) {

        public static CopyResult empty() {
            return new CopyResult(0, 0, null, Map.of(), Map.of());
        }
    }
}
