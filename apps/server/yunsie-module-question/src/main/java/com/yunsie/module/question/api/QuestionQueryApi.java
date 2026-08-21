package com.yunsie.module.question.api;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 题目查询契约（question 域对外 API）。
 * 供 exam（组卷/试卷快照）与 ai（题目解析）、learning-profile（练习/错题聚合）等域使用；
 * 其他域禁止直连 question 域 Entity/Mapper/表（模块边界铁律）。
 *
 * <p>快照语义：本契约一律返回不可变 QuestionSnapshot DTO（含 content_version），
 * 正式考试需在 exam 域自行落快照（exam-engine），不得引用当前题目。</p>
 *
 * <p>Stage 1.6 追加式扩展（仅新增方法/View，不改已有方法行为）：
 * {@link #listPracticeStats(Long)} 与 {@link #listMistakeStats(Long)} 供 learning-profile 聚合。</p>
 */
public interface QuestionQueryApi {

    /** 已发布题目快照（未发布/已删除返回 null） */
    QuestionSnapshot findPublishedSnapshot(Long questionId);

    boolean existsPublished(Long questionId);

    /** 按证书/题型/知识点过滤的已发布题目（供 exam 组卷用，MVP 简单查询） */
    List<QuestionSnapshot> findPublishedQuestions(Long certificateId, List<Integer> questionTypes,
                                                  List<Long> nodeIds, int limit);

    /**
     * 判分契约：题型判分唯一实现在 question 域（GradingService），
     * 其他域（exam）一律经本方法委托，禁止复制题型判分逻辑。
     *
     * @throws com.yunsie.common.exception.BizException 题型非法或答案无法标准化（ANSWER_INVALID）
     */
    boolean gradeSubmission(int questionType, String standardAnswer, String submittedRaw);

    /** 用户练习聚合（按知识点节点分组；空列表=无练习记录） */
    List<PracticeStatView> listPracticeStats(Long userId);

    /** 用户练习按日聚合（学习日历用；空列表=无练习记录） */
    List<PracticeDailyStatView> listPracticeDailyStats(Long userId);

    /** 用户错题聚合（按知识点节点分组，未解决错题；空列表=无） */
    List<MistakeStatView> listMistakeStats(Long userId);

    /**
     * 按知识点节点取已发布题目（练习安全视图，不含答案与解析）。
     * 仅已发布、关联任意给定节点、按 id 升序取 limit 条。
     */
    List<PracticeQuestionView> listPublishedByNodes(Collection<Long> nodeIds, int limit);

    /** 关联该知识节点的题目数（Stage 2.3A 节点删除守卫） */
    long countByNode(Long nodeId);

    /** 题目总数（含回收站；Stage 2.3B 运营看板） */
    long countAll();

    /** 指定状态题目数（Stage 2.3B 运营看板：待审核提醒） */
    long countByStatus(int status);

    /**
     * 练习安全视图（章节练习/针对性推荐用；不含答案与解析，杜绝提前泄露）。
     */
    record PracticeQuestionView(
            Long id,
            Long certificateId,
            Integer questionType,
            String stem,
            Integer difficulty,
            List<OptionSnapshot> options,
            List<Long> nodeIds) {
    }

    record QuestionSnapshot(
            Long id,
            Long certificateId,
            Integer questionType,
            String stem,
            String answer,
            String analysis,
            Integer difficulty,
            Integer source,
            Integer contentVersion,
            List<OptionSnapshot> options,
            List<Long> nodeIds) {
    }

    record OptionSnapshot(String optionKey, String content, Integer sort) {
    }

    record PracticeStatView(
            Long nodeId,
            Integer practiceCount,
            Integer correctCount,
            Integer wrongCount,
            Long totalResponseMs,
            LocalDateTime lastPracticeAt) {
    }

    record PracticeDailyStatView(
            java.time.LocalDate studyDate,
            Integer practiceCount,
            Integer correctCount,
            Long totalResponseMs) {
    }

    record MistakeStatView(
            Long nodeId,
            Integer mistakeCount,
            LocalDateTime lastMistakeTime) {
    }
}
