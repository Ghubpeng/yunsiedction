package com.yunsie.module.question.api;

import java.util.Map;

/**
 * 题库写入契约（question 域对外 API，Stage 2.3B 内容复制）。
 * 复制源证书已发布题目（题干/解析/答案/选项/知识点关联）到目标证书；
 * 知识点经 nodeIdMap 重映射；目标题目一律草稿（发布唯一通道=审核通过，question-bank 铁律）。
 */
public interface QuestionCommandApi {

    /**
     * 复制已发布题目。
     *
     * @param sourceCertId 源证书
     * @param targetCertId 目标证书
     * @param nodeIdMap    知识点节点 id 映射（源→新）
     * @return 复制的题目数（节点无法映射的题目跳过，不产生孤儿题）
     */
    int copyPublishedQuestions(Long sourceCertId, Long targetCertId, Map<Long, Long> nodeIdMap);
}
