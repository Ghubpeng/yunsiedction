package com.yunsie.module.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.question.dto.PracticeSubmitReq;
import com.yunsie.module.question.entity.Question;
import com.yunsie.module.question.entity.QuestionKnowledgeNode;
import com.yunsie.module.question.entity.QuestionMistake;
import com.yunsie.module.question.entity.QuestionOption;
import com.yunsie.module.question.entity.QuestionPracticeRecord;
import com.yunsie.module.question.enums.PracticeMode;
import com.yunsie.module.question.enums.QuestionStatus;
import com.yunsie.module.question.enums.QuestionType;
import com.yunsie.module.question.error.QuestionErrorCode;
import com.yunsie.module.question.mapper.QuestionKnowledgeNodeMapper;
import com.yunsie.module.question.mapper.QuestionMapper;
import com.yunsie.module.question.mapper.QuestionMistakeMapper;
import com.yunsie.module.question.mapper.QuestionOptionMapper;
import com.yunsie.module.question.mapper.QuestionPracticeRecordMapper;
import com.yunsie.module.question.service.GradingService;
import com.yunsie.module.question.service.MistakeService;
import com.yunsie.module.question.service.PracticeService;
import com.yunsie.module.question.vo.OptionVO;
import com.yunsie.module.question.vo.PracticeQuestionVO;
import com.yunsie.module.question.vo.PracticeSubmitResultVO;
import com.yunsie.module.subject.api.SubjectQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 练习实现（J 模型）。
 */
@Service
@RequiredArgsConstructor
public class PracticeServiceImpl implements PracticeService {

    private static final int MAX_SIZE = 50;

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper optionMapper;
    private final QuestionKnowledgeNodeMapper knowledgeNodeMapper;
    private final QuestionMistakeMapper mistakeMapper;
    private final QuestionPracticeRecordMapper practiceRecordMapper;
    private final GradingService gradingService;
    private final MistakeService mistakeService;
    private final SubjectQueryApi subjectQueryApi;

    @Override
    public List<PracticeQuestionVO> next(Long userId, int mode, Long certificateId, Long nodeId,
                                         Long cursor, int size, int pageNum, int pageSize) {
        PracticeMode practiceMode = PracticeMode.of(mode);
        if (practiceMode == null) {
            throw new BizException(QuestionErrorCode.PRACTICE_MODE_INVALID);
        }
        int limit = Math.min(Math.max(size, 1), MAX_SIZE);
        List<Question> questions = switch (practiceMode) {
            case SEQUENCE -> nextByCursor(certificateId, null, cursor, limit, "SEQUENCE");
            case CATEGORY -> nextByCursor(certificateId, null, cursor, limit, "CATEGORY");
            case KNOWLEDGE_POINT -> nextByNode(nodeId, pageNum, pageSize);
            case MISTAKE -> nextByMistakes(userId, pageNum, pageSize);
        };
        return questions.stream().map(this::toPracticeVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PracticeSubmitResultVO submit(Long userId, PracticeSubmitReq req) {
        Question question = questionMapper.selectById(req.questionId());
        if (question == null || QuestionStatus.of(question.getStatus()) != QuestionStatus.PUBLISHED) {
            throw new BizException(QuestionErrorCode.QUESTION_NOT_PUBLISHED);
        }
        String normalized = gradingService.normalizeSubmitted(question.getQuestionType(), req.answer());
        boolean correct = normalized.equals(question.getAnswer());

        QuestionPracticeRecord record = new QuestionPracticeRecord();
        record.setUserId(userId);
        record.setQuestionId(question.getId());
        record.setPracticeMode(req.mode());
        record.setSubmittedAnswer(normalized);
        record.setStandardAnswer(question.getAnswer());
        record.setCorrect(correct ? 1 : 0);
        record.setAnswerTime(LocalDateTime.now());
        record.setResponseTimeMs(req.responseTimeMs());
        record.setKnowledgeNodeId(req.nodeId());
        practiceRecordMapper.insert(record);

        if (correct) {
            mistakeService.recordCorrect(userId, question.getId());
        } else {
            mistakeService.recordWrong(userId, question.getId());
        }
        return new PracticeSubmitResultVO(question.getId(), correct, question.getAnswer(), question.getAnalysis());
    }

    /** 顺序/分类：证书过滤 + id 游标，仅已发布 */
    private List<Question> nextByCursor(Long certificateId, Long nodeId, Long cursor, int limit, String modeName) {
        if (certificateId == null) {
            throw new BizException(QuestionErrorCode.PRACTICE_MODE_INVALID);
        }
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .eq(Question::getCertificateId, certificateId)
                .eq(Question::getStatus, QuestionStatus.PUBLISHED.code());
        if (cursor != null && cursor > 0) {
            wrapper.gt(Question::getId, cursor);
        }
        wrapper.orderByAsc(Question::getId).last("LIMIT " + limit);
        return questionMapper.selectList(wrapper);
    }

    /** 知识点：节点校验（存在/启用/知识点或子知识点）；知识点含其子知识点 */
    private List<Question> nextByNode(Long nodeId, int pageNum, int pageSize) {
        if (nodeId == null) {
            throw new BizException(QuestionErrorCode.PRACTICE_MODE_INVALID);
        }
        SubjectQueryApi.KnowledgeNodeView node = subjectQueryApi.findNode(nodeId);
        if (node == null || node.enabled() == null || node.enabled() != 1
                || (node.nodeType() != 2 && node.nodeType() != 3)) {
            throw new BizException(QuestionErrorCode.NODE_ASSOC_INVALID);
        }
        java.util.List<Long> nodeIds = new java.util.ArrayList<>();
        nodeIds.add(nodeId);
        if (node.nodeType() == 2) {
            nodeIds.addAll(subjectQueryApi.findChildNodeIds(nodeId));
        }
        List<Long> questionIds = knowledgeNodeMapper.selectList(new LambdaQueryWrapper<QuestionKnowledgeNode>()
                        .in(QuestionKnowledgeNode::getNodeId, nodeIds))
                .stream().map(QuestionKnowledgeNode::getQuestionId).distinct().toList();
        if (questionIds.isEmpty()) {
            return List.of();
        }
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Page<Question> page = questionMapper.selectPage(new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<Question>()
                        .in(Question::getId, questionIds)
                        .eq(Question::getStatus, QuestionStatus.PUBLISHED.code())
                        .orderByAsc(Question::getId));
        return page.getRecords();
    }

    /** 错题：本人未解决错题，按最近答错倒序，仅已发布 */
    private List<Question> nextByMistakes(Long userId, int pageNum, int pageSize) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Page<QuestionMistake> page = mistakeMapper.selectPage(new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<QuestionMistake>()
                        .eq(QuestionMistake::getUserId, userId)
                        .eq(QuestionMistake::getStatus, 1)
                        .orderByDesc(QuestionMistake::getLastMistakeTime));
        List<Long> questionIds = page.getRecords().stream().map(QuestionMistake::getQuestionId).distinct().toList();
        if (questionIds.isEmpty()) {
            return List.of();
        }
        return questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .in(Question::getId, questionIds)
                .eq(Question::getStatus, QuestionStatus.PUBLISHED.code())
                .orderByAsc(Question::getId));
    }

    private PracticeQuestionVO toPracticeVO(Question question) {
        List<OptionVO> options = QuestionType.of(question.getQuestionType()) == QuestionType.TRUE_FALSE
                ? List.of()
                : optionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                        .eq(QuestionOption::getQuestionId, question.getId())
                        .orderByAsc(QuestionOption::getSort).orderByAsc(QuestionOption::getOptionKey))
                        .stream().map(o -> new OptionVO(o.getOptionKey(), o.getContent())).toList();
        return new PracticeQuestionVO(question.getId(), question.getQuestionType(), question.getStem(), options);
    }
}
