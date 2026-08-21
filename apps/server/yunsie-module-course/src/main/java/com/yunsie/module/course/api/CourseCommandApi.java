package com.yunsie.module.course.api;

import java.util.Map;

/**
 * 课程结构写入契约（course 域对外 API，Stage 2.3B 内容复制）。
 * 复制源证书全部课程（章/节结构 + 小节知识点关联）到目标证书：
 * 内容树归属经 subjectIdMap/nodeIdMap 重映射；视频不复制（video_key 置空，不伪造媒体文件）；
 * 目标课程一律草稿（发布仍走课程状态机）。
 */
public interface CourseCommandApi {

    /**
     * 复制课程结构。
     *
     * @param sourceCertId   源证书
     * @param targetCertId   目标证书
     * @param targetVersionId 目标知识版本（源无版本时为 null，课程 version_id 置空保持可空语义）
     * @param subjectIdMap   科目 id 映射（源→新；未映射保持 null）
     * @param nodeIdMap      章节节点 id 映射（源→新；未映射保持 null）
     * @return 复制的课程数
     */
    int copyCourseStructure(Long sourceCertId, Long targetCertId, Long targetVersionId,
                            Map<Long, Long> subjectIdMap, Map<Long, Long> nodeIdMap);
}
