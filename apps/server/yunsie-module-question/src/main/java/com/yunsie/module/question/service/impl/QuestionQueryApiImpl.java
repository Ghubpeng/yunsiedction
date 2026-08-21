package com.yunsie.module.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.question.api.QuestionQueryApi;
import com.yunsie.module.question.entity.Question;
import com.yunsie.module.question.entity.QuestionKnowledgeNode;
import com.yunsie.module.question.entity.QuestionMistake;
import com.yunsie.module.question.entity.QuestionOption;
import com.yunsie.module.question.entity.QuestionPracticeRecord;
import com.yunsie.module.question.enums.QuestionStatus;
import com.yunsie.module.question.mapper.QuestionKnowledgeNodeMapper;
import com.yunsie.module.question.mapper.QuestionMapper;
import com.yunsie.module.question.mapper.QuestionMistakeMapper;
import com.yunsie.module.question.mapper.QuestionOptionMapper;
import com.yunsie.module.question.mapper.QuestionPracticeRecordMapper;
import com.yunsie.module.question.service.GradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题目查询契约实现：一律返回不可变快照 DTO（含 content_version/node_ids），不暴露 Entity。
 * 判分委托 GradingService（题型判分唯一实现）。
 * Stage 1.6 追加：练习/错题聚合（供 learning-profile；仅新增，不改既有方法行为）。
 */
@Service
@RequiredArgsConstructor
public class QuestionQueryApiImpl implements QuestionQueryApi {

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper optionMapper;
    private final QuestionKnowledgeNodeMapper knowledgeNodeMapper;
    private final QuestionPracticeRecordMapper practiceRecordMapper;
    private final QuestionMistakeMapper mistakeMapper;
    private final GradingService gradingService;

    @Override
    public QuestionSnapshot findPublishedSnapshot(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null || QuestionStatus.of(question.getStatus()) != QuestionStatus.PUBLISHED) {
            return null;
        }
        return toSnapshot(question);
    }

    @Override
    public boolean existsPublished(Long questionId) {
        return findPublishedSnapshot(questionId) != null;
    }

    @Override
    public List<QuestionSnapshot> findPublishedQuestions(Long certificateId, List<Integer> questionTypes,
                                                         List<Long> nodeIds, int limit) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, QuestionStatus.PUBLISHED.code());
        if (certificateId != null) {
            wrapper.eq(Question::getCertificateId, certificateId);
        }
        if (questionTypes != null && !questionTypes.isEmpty()) {
            wrapper.in(Question::getQuestionType, questionTypes);
        }
        if (nodeIds != null && !nodeIds.isEmpty()) {
            List<Long> questionIds = knowledgeNodeMapper.selectList(new LambdaQueryWrapper<QuestionKnowledgeNode>()
                            .in(QuestionKnowledgeNode::getNodeId, nodeIds))
                    .stream().map(QuestionKnowledgeNode::getQuestionId).distinct().toList();
            if (questionIds.isEmpty()) {
                return List.of();
            }
            wrapper.in(Question::getId, questionIds);
        }
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        wrapper.orderByAsc(Question::getId).last("LIMIT " + safeLimit);
        List<Question> questions = questionMapper.selectList(wrapper);
        if (questions.isEmpty()) {
            return List.of();
        }
        List<Long> ids = questions.stream().map(Question::getId).toList();
        Map<Long, List<OptionSnapshot>> optionsByQuestion = loadOptions(ids);
        Map<Long, List<Long>> nodesByQuestion = loadNodeIds(ids);
        return questions.stream().map(q -> toSnapshot(q, optionsByQuestion, nodesByQuestion)).toList();
    }

    @Override
    public boolean gradeSubmission(int questionType, String standardAnswer, String submittedRaw) {
        // 判分唯一实现：委托 GradingService，任何域不得复制题型判分逻辑
        return gradingService.grade(questionType, standardAnswer, submittedRaw);
    }

    @Override
    public List<PracticeQuestionView> listPublishedByNodes(Collection<Long> nodeIds, int limit) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return List.of();
        }
        List<Long> questionIds = knowledgeNodeMapper.selectList(new LambdaQueryWrapper<QuestionKnowledgeNode>()
                        .in(QuestionKnowledgeNode::getNodeId, nodeIds))
                .stream().map(QuestionKnowledgeNode::getQuestionId).distinct().toList();
        if (questionIds.isEmpty()) {
            return List.of();
        }
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .in(Question::getId, questionIds)
                .eq(Question::getStatus, QuestionStatus.PUBLISHED.code())
                .orderByAsc(Question::getId)
                .last("LIMIT " + safeLimit));
        if (questions.isEmpty()) {
            return List.of();
        }
        List<Long> ids = questions.stream().map(Question::getId).toList();
        Map<Long, List<OptionSnapshot>> optionsByQuestion = loadOptions(ids);
        Map<Long, List<Long>> nodesByQuestion = loadNodeIds(ids);
        return questions.stream()
                .map(q -> new PracticeQuestionView(q.getId(), q.getCertificateId(), q.getQuestionType(),
                        q.getStem(), q.getDifficulty(),
                        optionsByQuestion.getOrDefault(q.getId(), List.of()),
                        nodesByQuestion.getOrDefault(q.getId(), List.of())))
                .toList();
    }

    @Override
    public long countByNode(Long nodeId) {
        if (nodeId == null) {
            return 0;
        }
        Long count = knowledgeNodeMapper.selectCount(new LambdaQueryWrapper<QuestionKnowledgeNode>()
                .eq(QuestionKnowledgeNode::getNodeId, nodeId));
        return count == null ? 0 : count;
    }

    @Override
    public long countAll() {
        Long count = questionMapper.selectCount(new LambdaQueryWrapper<Question>());
        return count == null ? 0 : count;
    }

    @Override
    public long countByStatus(int status) {
        Long count = questionMapper.selectCount(new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, status));
        return count == null ? 0 : count;
    }

    @Override
    public List<PracticeStatView> listPracticeStats(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<QuestionPracticeRecord> records = practiceRecordMapper.selectList(
                new LambdaQueryWrapper<QuestionPracticeRecord>()
                        .eq(QuestionPracticeRecord::getUserId, userId)
                        .isNotNull(QuestionPracticeRecord::getKnowledgeNodeId));
        Map<Long, long[]> stats = new LinkedHashMap<>(); // nodeId -> [count, correct, wrong, responseMs]
        Map<Long, LocalDateTime> lastAt = new LinkedHashMap<>();
        for (QuestionPracticeRecord r : records) {
            Long nodeId = r.getKnowledgeNodeId();
            long[] s = stats.computeIfAbsent(nodeId, k -> new long[4]);
            s[0]++;
            s[1] += r.getCorrect() != null && r.getCorrect() == 1 ? 1 : 0;
            s[2] += r.getCorrect() != null && r.getCorrect() == 1 ? 0 : 1;
            s[3] += r.getResponseTimeMs() == null ? 0 : r.getResponseTimeMs();
            LocalDateTime at = r.getAnswerTime();
            if (at != null && (lastAt.get(nodeId) == null || at.isAfter(lastAt.get(nodeId)))) {
                lastAt.put(nodeId, at);
            }
        }
        return stats.entrySet().stream()
                .map(e -> new PracticeStatView(e.getKey(), (int) e.getValue()[0], (int) e.getValue()[1],
                        (int) e.getValue()[2], e.getValue()[3], lastAt.get(e.getKey())))
                .toList();
    }

    @Override
    public List<PracticeDailyStatView> listPracticeDailyStats(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<QuestionPracticeRecord> records = practiceRecordMapper.selectList(
                new LambdaQueryWrapper<QuestionPracticeRecord>()
                        .eq(QuestionPracticeRecord::getUserId, userId));
        Map<java.time.LocalDate, long[]> stats = new LinkedHashMap<>(); // date -> [count, correct, responseMs]
        for (QuestionPracticeRecord r : records) {
            if (r.getAnswerTime() == null) {
                continue;
            }
            java.time.LocalDate date = r.getAnswerTime().toLocalDate();
            long[] s = stats.computeIfAbsent(date, k -> new long[3]);
            s[0]++;
            s[1] += r.getCorrect() != null && r.getCorrect() == 1 ? 1 : 0;
            s[2] += r.getResponseTimeMs() == null ? 0 : r.getResponseTimeMs();
        }
        return stats.entrySet().stream()
                .map(e -> new PracticeDailyStatView(e.getKey(), (int) e.getValue()[0],
                        (int) e.getValue()[1], e.getValue()[2]))
                .toList();
    }

    @Override
    public List<MistakeStatView> listMistakeStats(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<QuestionMistake> mistakes = mistakeMapper.selectList(new LambdaQueryWrapper<QuestionMistake>()
                .eq(QuestionMistake::getUserId, userId)
                .eq(QuestionMistake::getStatus, 1)); // 未解决
        if (mistakes.isEmpty()) {
            return List.of();
        }
        List<Long> questionIds = mistakes.stream().map(QuestionMistake::getQuestionId).distinct().toList();
        List<QuestionKnowledgeNode> links = knowledgeNodeMapper.selectList(new LambdaQueryWrapper<QuestionKnowledgeNode>()
                .in(QuestionKnowledgeNode::getQuestionId, questionIds));
        Map<Long, Long> nodeByQuestion = links.stream()
                .collect(Collectors.toMap(QuestionKnowledgeNode::getQuestionId, QuestionKnowledgeNode::getNodeId,
                        (a, b) -> a));
        Map<Long, long[]> stats = new LinkedHashMap<>(); // nodeId -> [mistakeCount]
        Map<Long, LocalDateTime> lastAt = new LinkedHashMap<>();
        for (QuestionMistake m : mistakes) {
            Long nodeId = nodeByQuestion.get(m.getQuestionId());
            if (nodeId == null) {
                continue;
            }
            long[] s = stats.computeIfAbsent(nodeId, k -> new long[1]);
            s[0] += m.getMistakeCount() == null ? 1 : m.getMistakeCount();
            LocalDateTime at = m.getLastMistakeTime();
            if (at != null && (lastAt.get(nodeId) == null || at.isAfter(lastAt.get(nodeId)))) {
                lastAt.put(nodeId, at);
            }
        }
        return stats.entrySet().stream()
                .map(e -> new MistakeStatView(e.getKey(), (int) e.getValue()[0], lastAt.get(e.getKey())))
                .toList();
    }

    private QuestionSnapshot toSnapshot(Question question) {
        List<Long> ids = List.of(question.getId());
        Map<Long, List<OptionSnapshot>> optionsByQuestion = loadOptions(ids);
        Map<Long, List<Long>> nodesByQuestion = loadNodeIds(ids);
        return toSnapshot(question, optionsByQuestion, nodesByQuestion);
    }

    private Map<Long, List<OptionSnapshot>> loadOptions(List<Long> questionIds) {
        return optionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                        .in(QuestionOption::getQuestionId, questionIds)
                        .orderByAsc(QuestionOption::getSort).orderByAsc(QuestionOption::getOptionKey))
                .stream()
                .collect(Collectors.groupingBy(QuestionOption::getQuestionId,
                        Collectors.mapping(o -> new OptionSnapshot(o.getOptionKey(), o.getContent(), o.getSort()),
                                Collectors.toList())));
    }

    private Map<Long, List<Long>> loadNodeIds(List<Long> questionIds) {
        return knowledgeNodeMapper.selectList(new LambdaQueryWrapper<QuestionKnowledgeNode>()
                        .in(QuestionKnowledgeNode::getQuestionId, questionIds))
                .stream()
                .collect(Collectors.groupingBy(QuestionKnowledgeNode::getQuestionId,
                        Collectors.mapping(QuestionKnowledgeNode::getNodeId, Collectors.toList())));
    }

    private QuestionSnapshot toSnapshot(Question q, Map<Long, List<OptionSnapshot>> optionsByQuestion,
                                        Map<Long, List<Long>> nodesByQuestion) {
        return new QuestionSnapshot(q.getId(), q.getCertificateId(), q.getQuestionType(), q.getStem(),
                q.getAnswer(), q.getAnalysis(), q.getDifficulty(), q.getSource(), q.getContentVersion(),
                optionsByQuestion.getOrDefault(q.getId(), List.of()),
                nodesByQuestion.getOrDefault(q.getId(), List.of()));
    }
}
